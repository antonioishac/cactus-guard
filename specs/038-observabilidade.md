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

## Tracing (OpenTelemetry)

- Propagação de `traceId` em todas as requisições.
- Span em chamadas a provedores externos.
- Span em avaliações de regras.
- Exportação para Jaeger ou Tempo.

## Alertas operacionais

| Condição | Ação |
|----------|------|
| Taxa de erro > 5% por 5 min | Alerta PagerDuty/Slack |
| Latência p99 > 2s | Alerta |
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
      probability: 1.0
```

## Observações para manutenção

- Não logar dados sensíveis (CPF, documento) em métricas.
- Usar labels com baixa cardinalidade.
- Revisar sampling de tracing em produção (reduzir para 0.1 se volume alto).
