# Spec 038 — Observabilidade (Métricas e Tracing)

## Objetivo

Permitir monitoramento operacional do sistema em produção, com métricas de negócio e tracing distribuído.

## Métricas (Micrometer + Prometheus)

### Métricas de negócio

| Métrica | Tipo | Labels |
|---------|------|--------|
| `cactusguard.alerts.created` | Counter | `severity`, `rule_code` |
| `cactusguard.alerts.assumed` | Counter | `severity` |
| `cactusguard.cases.decided` | Counter | `decision` |
| `cactusguard.kyc.completed` | Counter | `status` |
| `cactusguard.screening.matches` | Counter | `list_code`, `status` |
| `cactusguard.siscoaf.submitted` | Counter | `type` |
| `cactusguard.rules.evaluated` | Counter | `rule_code` |
| `cactusguard.rules.triggered` | Counter | `rule_code` |

### Métricas técnicas

| Métrica | Tipo | Observação |
|---------|------|------------|
| `http.server.requests` | Timer | Já vem do Spring Boot Actuator |
| `jvm.memory.used` | Gauge | Padrão |
| `db.pool.active` | Gauge | HikariCP |
| `cache.gets` | Counter | Redis hit/miss |
| `integration.screening.duration` | Timer | Por provedor |
| `integration.siscoaf.duration` | Timer | Por tipo |

### Endpoint

```
GET /actuator/prometheus
```

## Tracing (OpenTelemetry) — implementado (Fase 1, fechamento)

- **Stack**: `spring-boot-starter-opentelemetry` (Boot 4.1: Micrometer Tracing + bridge OTel + exportador OTLP/HTTP). Propagação **W3C `traceparent`**.
- **Exportação desligada por padrão** (dev não exige coletor). Ligar: `CACTUS_OTEL_ENABLED=true` + `CACTUS_OTEL_ENDPOINT=http://coletor:4318` (traces em `/v1/traces`; métricas OTLP opcionais com `CACTUS_OTEL_METRICS_ENABLED=true`). Sampling: `CACTUS_OTEL_SAMPLING` (prod 0,1; dev 1,0). `CACTUS_ENV` vira `deployment.environment.name`. Sem exportação o traceId/spanId continuam existindo (logs, `X-Trace-Id`, outbox, Kafka).
- **traceId unificado**: o `TraceIdFilter` (ordem logo após o filtro de observação HTTP do Boot, antes do Spring Security — cobre 401/403) devolve em `X-Trace-Id` e no `traceId` do corpo de erro o **traceId do OTel** (32 hex) = `trace.id` dos logs = traceId do coletor. `X-Trace-Id` enviado pelo cliente **não** vira o traceId (formato livre): fica no span (`client.trace_id`) e no MDC `clientTraceId`; para continuar um trace externo use `traceparent`. Sem tracer, comportamento legado (aceita `X-Trace-Id` válido ou gera UUID).
- **Spans**: HTTP server; **repositórios Spring Data** (`db.repository`, tags `repository`/`method`, `RepositoryObservationAspect` — substitui o span JDBC do driver; SQL cru fora de repositório, ex. relay/métricas, não gera span); **Kafka** producer e consumer (`spring.kafka.template/listener.observation-enabled`, contexto nos headers da mensagem); `outbox.relay`; **RestClient** dos providers externos KYC/Screening/SISCOAF (`http.client.requests`, propagação de `traceparent`; as chamadas passam por Resilience4j, o span fica dentro do circuit breaker/retry); avaliação de regras via consumidor.
- **Ponta a ponta HTTP → outbox → Kafka → consumidor**: `DomainEventPublisher` grava o `traceparent` do request nos headers da linha do outbox (mesma transação); o `OutboxRelay` (outra thread, depois do commit) abre `outbox.relay` como filho desse contexto e envia dentro dele ⇒ span `send` do producer e span `receive/process` do consumidor entram no **mesmo trace** do request. O header Kafka `traceId` e o `traceId` do envelope continuam presentes (agora = traceId do OTel).
- **Atributo `tenant.id`** (UUID, nunca PII) em todos os spans: hook em `TenantContext.set` (span corrente: HTTP após o JWT, consumidor dentro do `runAs`) + `ObservationFilter` (demais observações, como chave de **alta** cardinalidade ⇒ atributo de span, jamais tag de métrica).
- **Logs**: dev = texto com `[traceId,spanId] [tenant=…]`; perfil `prod` (`application-prod.yml`) = **JSON ECS** com os campos MDC `traceId`, `spanId` e `tenantId` no nível raiz (validado). Formato trocável por `CACTUS_LOG_FORMAT` (`ecs|logstash|gelf`). Sem exportador OTLP de logs (o coletor lê o stdout).

### Métricas novas (além das acima e das do outbox)

| Métrica | Tipo | Tags | Observação |
|---|---|---|---|
| `cactusguard.alerts.created` | Counter | `severity`, `rule_code` [+`tenant`] | agora incrementada em `AlertCreationService` (alertas por regra/severidade) |
| `cactus.rules.evaluation.duration` | Timer (histograma + SLO 100ms…2s) | `outcome` [+`tenant`] | latência da avaliação das regras **por transação** (consumidor por evento) |
| `cactus.cases.by.status` | Gauge | `status` [+`tenant`] | snapshot a cada 30 s (`OperationalMetrics`) |
| `cactus.dlq.pending`, `cactus.dlq.oldest.age.seconds` | Gauge | [`tenant`] | DLQ pendente e idade da mais antiga |
| `cactus.audit.chain.broken`, `cactus.audit.chain.tenants.broken` | Counter / Gauge | — | cadeia de auditoria (spec 047) |
| `cactus.ratelimit.rejected` | Counter | `scope` | `login_ip`, `api_ip`, `api_tenant`, `api_user`, `search_user` |

**Gauges sem série**: `cactus.cases.by.status` só aparece depois que existe ao menos um caso do status; use `absent()`/`or vector(0)` nas queries.

**Cardinalidade da tag `tenant`**: **desligada por padrão** (métricas agregadas da plataforma; o detalhe por tenant está em spans e logs). `CACTUS_METRICS_TENANT_TAG=true` liga; `MetricTags` admite no máximo `CACTUS_METRICS_TENANT_TAG_MAX` (50) UUIDs distintos por processo e agrega o resto em `tenant="other"`. Valor = UUID (nunca nome/CNPJ). Com 50 tenants × 5 severidades × ~30 regras o contador de alertas chega a ~7,5 mil séries: ligar só com poucos tenants ou reduzir o teto.

## SLOs (queries Prometheus)

| SLO | Objetivo | Query |
|---|---|---|
| Latência de API p99 | < 2 s em 99% das janelas de 5 min | `histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{application="cactusguard",uri!~"/actuator.*"}[5m])))` (exige `management.metrics.distribution.percentiles-histogram.http.server.requests=true` para ter buckets) |
| Disponibilidade (erro 5xx) | < 1% em 5 min | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))` |
| Avaliação de regras por transação p99 | < 1 s | `histogram_quantile(0.99, sum by (le) (rate(cactus_rules_evaluation_duration_seconds_bucket{outcome="success"}[5m])))` |
| Backlog do outbox | < 1.000 eventos por 5 min | `max_over_time(cactus_outbox_backlog[5m]) > 1000` (violação) |
| Idade do evento mais antigo no outbox | < 60 s | `cactus_outbox_oldest_age_seconds > 60` por 5 min |
| DLQ pendente | 0 mensagens com mais de 1 h | `cactus_dlq_oldest_age_seconds > 3600` (e `cactus_dlq_pending > 0`) |
| Integridade da auditoria | 0 quebras | `increase(cactus_audit_chain_broken_total[1d]) > 0` ou `cactus_audit_chain_tenants_broken > 0` ⇒ **página imediata** |
| Rate limit | < 1% das requisições | `sum(rate(cactus_ratelimit_rejected_total[5m]))` vs. total de requisições |

## Alertas operacionais

| Condição | Ação |
|----------|------|
| Taxa de erro > 5% por 5 min | Alerta PagerDuty/Slack |
| Latência p99 > 2s | Alerta (query na seção SLOs) |
| Cadeia de auditoria quebrada | Página imediata (security event `AUDIT_CHAIN_BROKEN`) |
| DLQ pendente > 1 h | Alerta |
| Fila de migração > 10k registros | Alerta |
| Redis down | Alerta + fallback |

## Configuração

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,metrics
  metrics:
    tags:
      application: cactusguard
  tracing:
    sampling:
      probability: ${CACTUS_OTEL_SAMPLING:0.1}   # dev: 1.0
    export:
      enabled: ${CACTUS_OTEL_ENABLED:false}
```

## Observações para manutenção

- Não logar dados sensíveis (CPF, documento) em métricas.
- Usar labels com baixa cardinalidade.
- Sampling de produção 0,1 por padrão; revisar conforme volume. Spans nunca carregam PII (só ids, UUID do tenant, nomes de repositório/método).
- Riscos residuais: spans de repositório só cobrem Spring Data (não SQL cru nem o driver); 100% de amostragem em dev; cardinalidade de `rule_code` depende de regras criadas por tenant (hoje dezenas).
