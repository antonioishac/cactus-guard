# 047 — Padrões técnicos e arquitetura do backend (CactusGuard)

> Renumerada de `007-padroes-tecnicos-backend.md` (havia dois arquivos 007). Status de implementação de cada decisão: ver `README.md` → "Status de implementação vs specs".

Complementa a visão geral do projeto. Define como o código Java do **CactusGuard** é organizado, quais padrões usar e quando.

## Versões e linguagem

- **Java 25+** com uso deliberado de recursos modernos: `records` para DTOs imutáveis, `Optional` em buscas, streams/lambdas com critério, virtual threads habilitadas para I/O-bound (screening, chamadas externas, consultas concorrentes), pattern matching e sealed classes onde modelam bem conjuntos fechados de estados (ex.: resultado de screening, status de alerta, decisão de case).
- **Spring Boot 4.x** (versão estável mais recente; mínimo 4.1.2) como framework, alinhado ao Java 25+ (suporte nativo a virtual threads).

## Arquitetura: monolito modular + hexagonal seletivo

Hexagonal (ports & adapters) **apenas** nos módulos com regra de negócio real e fronteira externa instável ou configurável:

- **Screening**
- **Motor de Regras / Monitoramento**
- **Comunicação SISCOAF**
- **Ingestão de Dados**

Os demais módulos (KYC/cadastro, Case Management, Relatórios, Auditoria, Alertas operacionais) usam camadas convencionais (controller → service → repository) para evitar over-engineering.

Onde hexagonal se aplica:
- `domain/` — modelo de domínio e regras puras (sem Spring/JPA).
- `application/` — casos de uso que orquestram o domínio.
- `ports/` — interfaces (ex.: `ScreeningPort`, `RuleEnginePort`, `SiscoafPort`, `DataIngestionPort`).
- `adapters/` — implementações concretas (entrada: controllers; saída: clientes HTTP, repositórios, produtores Kafka).

## Package base

**`br.com.cactus.guard`** + o módulo correspondente.  
Todo código do backend nasce sob este pacote raiz.

## Organização de pacotes (módulos convencionais)

```
br.com.cactus.guard.<modulo>/          # ex: br.com.cactus.guard.kyc, br.com.cactus.guard.case
├── controller/
├── service/
├── entity/
├── model/
├── dto/
├── repository/
└── mapper/
```

## Organização de pacotes (módulos com hexagonal)

```
br.com.cactus.guard.<modulo>/          # ex: br.com.cactus.guard.screening, br.com.cactus.guard.monitoring
├── domain/
├── application/
├── ports/
└── adapters/
```

O módulo compartilhado (`common`) também vive sob `br.com.cactus.guard.common`.

## Mapeamento e Lombok

- Todo mapeamento entidade ↔ DTO via **MapStruct**.
- Lombok (`@Getter`/`@Setter`, `@Builder`, `@RequiredArgsConstructor`) nas entidades e services.
- Preferir `record` em DTOs imutáveis.

## Padrões de projeto

- **Strategy**: para variações de regras de monitoramento, classificação de risco e validações de screening.
- **Circuit breaker**: **Resilience4j** em toda chamada a serviço externo (provedores de listas, SISCOAF, APIs de dados).
- **Eventos de domínio** (Kafka + outbox transacional) — **implementado** (Fase 1b, spec 048 §4/§5).

### Eventos (decisão final)
- **Fluxo**: serviço de negócio → `DomainEventPublisher.publish(...)` grava em `outbox_events` **na mesma transação** (`MANDATORY`) → `OutboxRelay` (`SELECT ... FOR UPDATE SKIP LOCKED`, por tenant via `TenantRunner`, lote, `acks=all`, producer idempotente, circuit breaker `kafka-outbox`) publica e marca `published_at` → tópico `cactus.<agregado>s.v1` (chave = `aggregate_id`) → consumidor → `processed_events` (idempotência) + efeito **na mesma transação**.
- **Garantia**: at-least-once ponta a ponta + dedup por id do evento = efeito exatamente uma vez. Kafka fora do ar: o negócio segue, o outbox acumula, o relay retoma com backoff (1s…5min). Só a **cabeça de cada agregado** é elegível: ordem por agregado preservada mesmo com backoff e várias instâncias.
- **Envelope** (CloudEvents-like): `id, type, source, tenantId, traceId, occurredAt, schemaVersion, dataContentType, data`. Headers Kafka: `tenantId, traceId, eventType, eventId, schemaVersion`. **`data` sem PII** (só ids, enums, valores monetários como texto, datas ISO; `PiiGuard` rejeita chaves de PII na publicação; sem justificativas/descrições livres). Eventos são **internos**: nunca expostos em canal do cliente (tipping-off).
- **Catálogo** (tópico ← tipo): `cactus.transactions.v1` ← `cactus.transaction.received`; `cactus.alerts.v1` ← `cactus.alert.created`; `cactus.cases.v1` ← `cactus.case.decided`, `cactus.case.approved`, `cactus.case.approval-rejected`; `cactus.communications.v1` ← `cactus.communication.created`, `cactus.communication.submitted` (inclui COE automático); `cactus.parties.v1` ← `cactus.party.risk-changed`, `cactus.party.asset-freeze-activated`. Cada tópico tem `<tópico>.dlq` (retenção 90 dias). Criados pela aplicação (`NewTopic`; `cactus.events.topics.*`: partições 6 / RF 3 em dev; `create=false` quando gerenciados por IaC).
- **Consumidor** `monitoring-transactions` (grupo `cactus-monitoring`): `TransactionReceived` → `RuleEngineService.evaluateForTransaction` (mesmos cenários do batch com filtro opcional de parte; só cenários transacionais; COE já é síncrono; screening/watchlist e janelas longas seguem no batch de 6h). Roda no tenant do **header**; envelope e header divergentes, tenant inválido ou agregado inexistente no tenant ⇒ erro não recuperável.
- **Erros**: `DefaultErrorHandler` com backoff exponencial (4 tentativas, 0,5s×2, teto 10s) **bloqueante** (preserva a ordem por agregado; por isso **não há tópicos `.retry`** — decisão deliberada). Esgotado ou não recuperável (payload inválido, tenant, agregado) ⇒ grava `dead_letter_events` + publica em `<tópico>.dlq`; o offset só avança depois disso (nunca se descarta). Mensagem sem tenant válido é registrada no tenant `platform`.
- **DLQ operacional** (ADMIN/SUPERVISOR): `GET /api/v1/events/dead-letters`, `POST .../{id}/reprocess` (republica no tópico de origem; observação opcional), `POST .../{id}/discard` (**justificativa obrigatória**, ≥ 20 caracteres; a linha nunca é apagada). Ambos auditados (`DLQ_REPROCESSED`, `DLQ_DISCARDED`).
- **Configuração**: `cactus.events.enabled` (perfil `test`/H2: `false` ⇒ publisher noop). Bootstrap `CACTUS_KAFKA_BOOTSTRAP` (dev: 3 nós em 127.0.0.1:19092/29092/39092). Produção: `CACTUS_KAFKA_SECURITY_PROTOCOL=SASL_SSL` + `SPRING_KAFKA_PROPERTIES_SASL_MECHANISM=SCRAM-SHA-512` + `SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG` (do cofre), ou mTLS com `SSL` + `SPRING_KAFKA_SSL_BUNDLE`; nada de segredo no repositório. Limpeza: outbox publicado e `processed_events` expurgados após N dias (`cactus.events.cleanup.*`; não são dado regulatório).
- **Observabilidade**: `traceId` do request → header do outbox → header Kafka → MDC do consumidor (tenant idem). Métricas: `cactus.outbox.backlog`, `cactus.outbox.oldest.age.seconds`, `cactus.outbox.published`, `cactus.outbox.publish.failures`, `cactus.events.consumed`, `cactus.events.duplicates`.
- **Testes**: `EventEnvelopeAndPiiTest` (unit) e `EventsKafkaIT` (Testcontainers Kafka + PostgreSQL: transação→alerta, broker pausado→outbox→religa→exatamente um efeito, veneno→DLQ→reprocesso/descarte, isolamento de tenant).
- **Risco residual**: relay + consumidor no mesmo processo; schema registry/compatibilidade de `schemaVersion` ainda não automatizados; DLQ do tenant `platform` só é visível a PLATFORM_ADMIN.

## Banco de dados

- **Migrations: Flyway** (SQL puro versionado).
- Índices obrigatórios desde a criação da tabela em campos de filtro, busca e ordenação (especialmente em `Transaction`, `Alert`, `Party`, `ScreeningResult`).
- **Paginação obrigatória** (`Pageable`) em todas as listagens.
- Busca e filtros pelos campos principais de cada entidade relevante.
- Revisar `EXPLAIN ANALYZE` em queries de relatório e monitoramento antes de considerar a feature pronta.

### Full-Text Search (PostgreSQL)

Para buscas textuais (nome, documento, descrição de alerta, justificativa etc.), **não usar `LIKE`/`ILIKE` como padrão**.

- Usar **Full-Text Search** nativo do PostgreSQL (`tsvector` + `tsquery`).
- Criar coluna gerada ou coluna materializada `search_vector tsvector` nas entidades que precisam de busca textual.
- Criar índice GIN sobre a coluna `tsvector`.
- No JPA/Hibernate:
  - Mapear a coluna `tsvector` (pode ser somente leitura).
  - Usar `@Formula` ou query nativa / Criteria com função `to_tsvector` / `plainto_tsquery` quando necessário.
  - Preferir queries nativas ou Specification para buscas full-text complexas.
- Manter `LIKE` apenas para casos pontuais e de baixo volume (ex.: busca exata de documento com prefixo curto).
- Toda busca textual exposta em API deve usar full-text search.

## Autenticação e autorização

- **Spring Security + OAuth2 + JWT**.
- Backend atua como **OAuth2 Resource Server**.
- **Provedor inicial**: JWT emitido pelo próprio sistema ou Authorization Server leve no MVP.
- **Integração futura com Azure AD**: arquitetura preparada para trocar o provedor OIDC sem impacto no restante do sistema (configuração por tenant ou por ambiente).
- **Multi-tenancy** (decisão final, spec 048 B1 — **implementado**, migrations V33–V38):
  - **Modelo**: schema único; `tenant_id UUID NOT NULL` + FK + índice em toda tabela de dado de cliente. Tabelas **globais** (sem tenant): `roles`, `screening_lists`, `screening_entries`, `high_risk_jurisdictions`, `report_definitions`, `monitoring_rule_templates`, `tenants`, `flyway_schema_history`. `refresh_tokens` tem `tenant_id` mas **sem RLS** (o refresh chega sem tenant). `user_roles` herda o isolamento de `users` (policy com `EXISTS`). Tenants fixos: `default` (`…0001`, dados anteriores), `platform` (`…0002`, só operadores `PLATFORM_ADMIN`).
  - **Fluxo**: JWT (claim `tenant_id`, nunca header/parâmetro) → `JwtAuthenticationFilter` valida tenant existente/ACTIVE (senão 403) → `TenantContext` (ThreadLocal + MDC `tenantId`) → Hibernate 7 `@TenantId` em `TenantScoped` (`CurrentTenantIdentifierResolver` preenche o INSERT e filtra HQL/Criteria/find/DML em lote) → `TenantAwareDataSource` grava `app.tenant_id` (GUC de sessão) **a cada empréstimo de conexão do pool** (sobrescreve sempre, inclusive com vazio ⇒ nenhum vazamento entre requisições) → **RLS** (`ENABLE` + `FORCE`, `USING/WITH CHECK tenant_id = app_current_tenant()`). Sem tenant: sentinela `NO_TENANT` ⇒ filtro e RLS devolvem zero linhas e INSERT falha na FK (fail-closed). Trocar de tenant dentro de transação ativa é proibido (`TenantContext.callAs`).
  - **Por que `set_config` no checkout e não `SET LOCAL`/aspecto**: cobre leitura fora de transação, `JdbcTemplate` e qualquer consumidor do `DataSource`; não depende de sincronização de transação.
  - **Queries nativas** não recebem o filtro do Hibernate: `PartyRepository` (full-text) filtra `tenant_id` explicitamente via SpEL; `DocumentRehashService` (JDBC) idem; o RLS repete a regra.
  - **Unicidades por tenant** (V36): `parties.document_hash`, `monitoring_rules.code`, `cases.case_number`, `case_number_counters` (PK `tenant_id, counter_year`), `alerts.dedup_key` (abertos), COE por transação, declaração negativa por ano, `users.email`, `user_devices.token`. **`users.username` é único globalmente** (login não pede tenant; o username identifica o tenant — função `SECURITY DEFINER app_find_user_tenant`, único ponto que enxerga usuários de todos os tenants e devolve só o UUID).
  - **Assíncrono e jobs**: `TenantTaskDecorator` (auto-aplicado ao executor `@Async`) copia tenant + MDC e limpa ao final; eventos (`CriticalAlertCreatedEvent`, `CasePendingApprovalEvent`) carregam `tenantId` e o listener faz `runAs(event.tenantId())`; `@Scheduled` (RuleScheduler, PepExpiryJob, KycExpirationJob, DataRetentionJob, CaseDeadlinePushJob, LegacyPiiEncryptionRunner) iteram `TenantRunner.forEachActiveTenant` (falha isolada por tenant; SUSPENDED e `platform` ignorados). Relatórios gravam em `reports/<tenantId>/`.
  - **Cache**: `TenantAwareCacheManager` (decorator de qualquer `CacheManager`) usa o cache físico `<tenantId>:<nome>` ⇒ chaves Redis `<tenantId>:rules::…`; `allEntries` limpa só o tenant.
  - **Regras**: catálogo global `monitoring_rule_templates` (semente = regras do `default`); `MonitoringRuleService.provisionFromTemplates()` copia para o tenant novo com a versão 1.
  - **Papéis de banco (produção)**: *migration* = dono do schema com `BYPASSRLS` (ou superusuário; é também o dono da função `SECURITY DEFINER`); *runtime* = `NOSUPERUSER NOBYPASSRLS`, **não dono** das tabelas, só `SELECT/INSERT/UPDATE/DELETE` + `USAGE` em sequências. Configurar `SPRING_FLYWAY_URL/USER/PASSWORD` (migration) separados de `CACTUS_DB_USERNAME/PASSWORD` (runtime). Superusuário ignora RLS (dev local: `app_user` é superusuário ⇒ só o filtro Hibernate vale; o RLS é provado em `TenantIsolationIT` com papel não privilegiado).
  - **Plataforma**: role `PLATFORM_ADMIN` (nenhuma role de negócio ⇒ 403 em todo endpoint de dado de cliente); `/api/v1/platform/tenants` (spec 033). Tudo auditado (no tenant `platform` e no tenant provisionado).
  - **Testes**: `TenantIsolationIT` (PostgreSQL real, papel sem bypass: repository, JPQL/DML em lote, SQL nativo, API, fail-closed, pool, unicidade, jobs, plataforma) e `TenantPropagationTest` (@Async, evento, TenantRunner, cache). Perfil `test` (H2) usa `cactus.tenant.fallback-id` = tenant `default`; **dev/prod não têm fallback**.
  - **FKs compostas (V42–V43)**: toda tabela-pai por tenant tem `UNIQUE (tenant_id, id)` e toda FK filha entre tabelas por tenant é `(tenant_id, x_id) → pai (tenant_id, id)` (63 FKs, `fkc_<tabela>_<coluna>`); referência cruzada entre tenants é rejeitada pelo próprio banco (a FK ignora RLS). Coluna anulável segue opcional (MATCH SIMPLE). Prova: `TenantHardeningIT`. Nova FK entre tabelas por tenant **deve** seguir o padrão composto.
  - **Pepper por tenant (V44)**: `DocumentHash.of(doc)` deriva a chave do tenant do contexto por HKDF-SHA256 (`DOCUMENT_HASH_KEY` + `tenant:<id>`); sem tenant lança exceção; `ofPlatform` (chave mestre) só para o CNPJ do tenant (unicidade global). Migração única na subida (`DocumentHashMigrationRunner`, `tenants.hash_version` 1→2). Ver spec 040.
  - **Segurança de login (V44)**: `security_events` (plataforma, sem RLS, append-only, nunca grava senha; username mascarado + HMAC) alimenta o bloqueio progressivo (`security.login-protection.*`: 5 falhas/usuário e 20/IP em 15 min ⇒ bloqueio de 15 min, HTTP 429 genérico igual para usuário existente ou não; sucesso zera o contador do usuário). Consulta: `GET /api/v1/platform/security-events` (`PLATFORM_ADMIN`).
  - **Riscos residuais**: SQL injection poderia chamar `set_config('app.tenant_id', …)` (prevenção: sem SQL dinâmico com entrada externa); contadores do bloqueio progressivo vêm do banco (sem cache/Redis); `high_risk_jurisdictions` de origem `INTERNAL` ainda é global; troca de senha temporária no primeiro acesso ainda não é forçada.

### Integridade da auditoria (V45) — implementado
- **Cadeia de hash por tenant calculada no banco**: `hash = SHA-256(prev_hash || jsonb canônico do registro sem hash)` (função `audit_row_hash`, a mesma no trigger e na verificação). Trigger `BEFORE INSERT` em `audit_log` serializa por tenant com `SELECT … FOR UPDATE` em `audit_chain_heads` ⇒ inserts concorrentes não bifurcam (`chain_seq` 1,2,3… único por tenant). Cabeça só muda dentro do trigger (`pg_trigger_depth() >= 2`); sem DELETE/TRUNCATE.
- **Histórico anterior à V45 não é encadeado** (reescrevê-lo exigiria desligar o append-only da V22): `chain_seq/hash` nulos. Cada tenant começa num registro **gênesis** (`AUDIT_CHAIN_GENESIS`, seq 1, `prev_hash` = hash de gênesis do tenant); tenants novos nascem com a cabeça no primeiro insert.
- **Verificação**: `GET /api/v1/audit/integrity` (AUDITOR/SUPERVISOR/ADMIN, tenant do JWT) recalcula no banco e devolve `ok` ou o primeiro registro divergente (`HASH_MISMATCH`, `BROKEN_LINK`, `SEQUENCE_GAP`, `HEAD_MISMATCH` cauda truncada/acrescentada, `ANCHOR_MISMATCH`). Job diário `AuditChainJob.verifyAll` (03:30) por tenant (inclui `platform`): quebra ⇒ log ERROR, `security_events` `AUDIT_CHAIN_BROKEN`, linha na própria trilha e métricas `cactus.audit.chain.*`.
- **Ancoragem**: job horário grava a cabeça em `audit_chain_anchors` (append-only) e publica `cactus.audit.anchor` (tópico `cactus.audit.v1`, só `headSeq/headHash/anchoredAt`) pelo outbox, para prova externa; a verificação confere cada âncora contra a linha de mesma `chain_seq`.
- **Testes**: `AuditChainIT` (PostgreSQL real: concorrência linear, cadeias independentes, adulteração como superusuário detectada, cabeça/âncora, papel de runtime sem escrita direta).
- **Riscos residuais**: transações que auditam no mesmo tenant se serializam até o commit (lock da cabeça) e inversões de ordem de locks podem gerar deadlock raro (o banco aborta uma; a requisição falha e é repetida); quem tem superusuário **e** refaz toda a cadeia + cabeça só é pego por âncora externa (o evento Kafka é a prova fora do banco: arquivar `cactus.audit.v1` em storage imutável); a verificação completa é O(n) por tenant (diária; avaliar verificação incremental a partir da última âncora quando houver milhões de linhas).

### Rate limit e IP real (Fase 1) — implementado
- **Nunca global**: `RateLimitFilter` limita por **IP** no login (`login-per-ip` 10/min) e, no restante, por **IP** (`api-per-ip` 1000/min, folgado por NAT), **tenant** (`api-per-tenant` 3000/min) e **usuário** (`api-per-user` 100/min; `search-per-user` 30/min). Chaves `login:ip:<ip>`, `api:tenant:<id>`, `api:user:<tenant>:<user>`; 429 com `Retry-After` real e métrica `cactus.ratelimit.rejected{scope}`. Por username **não** há limite no filtro (permitiria a um atacante travar o login de uma vítima): isso fica com o bloqueio progressivo (`security.login-protection`, por usuário e por IP, resposta idêntica para usuário inexistente).
- **Backend**: `cactus.ratelimit.backend=redis` (padrão; janela fixa de 60 s via Lua `INCR+PEXPIRE`, compartilhada entre instâncias) ou `memory` (bucket4j por instância; perfil de teste). Redis indisponível ⇒ contingência local por instância durante 30 s (nunca derruba o login de todos); timeouts do Redis 500 ms/1 s. Janela fixa admite rajada de até 2× o limite na virada.
- **IP atrás de proxy**: `server.forward-headers-strategy=none` e `ClientIpResolver` próprio: `X-Forwarded-For` só é considerado se o par TCP estiver em `CACTUS_TRUSTED_PROXIES` (IPs/CIDRs separados por vírgula; padrão vazio = nenhum); percorre da direita para a esquerda pulando proxies confiáveis; entradas inválidas encerram a busca. O mesmo IP alimenta o limitador, o `LoginGuard`, `data_access_log` (reveal de PII) e a auditoria. Atenção: com `none` o esquema (`https`) atrás do balanceador não é reescrito — HSTS é cabeçalho da aplicação e o TLS termina no balanceador.
- **Testes**: `RateLimitFilterTest` (2 IPs não se bloqueiam; `X-Forwarded-For` forjado de origem não confiável é ignorado; proxy confiável identifica o cliente real; direita→esquerda; lixo no cabeçalho).
- **Riscos residuais**: requisições rejeitadas pelo Spring Security (401/403) não passam pelo limitador (ele roda depois da cadeia de segurança); o limitador de memória é por instância; IPv6 pode ser rotacionado dentro de um /64 (considerar agregar por /64).

## Tratamento de erros e logging

- `@ControllerAdvice` + `@ExceptionHandler` centralizado no módulo `common`.
- Resposta de erro padronizada (código, mensagem, timestamp, trace id).
- Log estruturado com **trace id / correlation id** propagado inclusive em chamadas externas e em eventos Kafka.
- Padrão consistente em todos os módulos (incluindo logs auditáveis de PLD).

## Documentação OpenAPI

- springdoc com `@Tag` / `@Operation` / `@SecurityRequirement`.
- Respostas de erro padrão (400, 401, 403, 404, 422, 500) documentadas **globalmente** via customizer no módulo `common` (sem anotação manual repetida em cada controller).
- Anotar `@ApiResponse` manualmente apenas para erros específicos de um endpoint.

## Observações específicas para PLD

- Toda ação relevante (criação de alerta, decisão de case, comunicação ao COAF, alteração de regra, mudança de status PEP) deve gerar evento e registro em `AuditLog`.
- O motor de regras e o screening devem ser testáveis isoladamente (graças ao hexagonal).
- Performance e particionamento de tabelas de alto volume (transações, alertas, logs) devem ser considerados desde as primeiras migrations.

## Particionamento de `transactions` (V48–V50) — implementado

- `transactions` é `PARTITION BY RANGE (transaction_date)`, 1 partição por mês em UTC (`transactions_pAAAAMM`) + `transactions_default`. PK `(tenant_id, id, transaction_date)`; o JPA continua com `@Id` apenas em `id` (entity inalterada). Unicidade de `id` entre meses não é garantida pelo banco (UUID gerado pela aplicação).
- FKs das filhas mantidas **no banco**: `alert_transactions`, `siscoaf_communications` e `transactions.reversal_of_id` ganharam `transaction_date`/`reversal_of_date` com FK composta `(tenant_id, x_id, x_date)`; trigger BEFORE preenche a data a partir de `(tenant_id, x_id)` e rejeita referência inexistente/de outro tenant. `transaction_date` é imutável (trigger).
- RLS: toda partição (inclusive DEFAULT) tem `ENABLE + FORCE` + policy `tenant_isolation` própria e os GRANTs da tabela-mãe (consulta direta à partição usa o RLS da partição).
- Gestão: `TransactionPartitionJob` (na subida e diariamente 02:30; `cactus.partitions.months-ahead`=3, `cactus.partitions.enabled`) chama `ensure_transaction_partitions()` (SQL `SECURITY DEFINER`, advisory lock, idempotente). Linhas na DEFAULT = erro + gauge `cactus.transactions.default_partition_rows`; criar a partição do mês move as linhas da DEFAULT. Sem `pg_partman` (não necessário).
- Retenção: **nenhuma partição é removida** (>= 10 anos, Circular 3.978). Arquivamento futuro: após 10 anos e aprovação jurídica, `ALTER TABLE transactions DETACH PARTITION ...` + dump/arquivo; as FKs das filhas exigem arquivar antes as filhas correspondentes.
- Regra para novas consultas: sempre filtrar por `transaction_date` (poda); consulta só por `id`/`end_to_end_id` sonda todas as partições.
- Candidatas a particionar depois: `alerts` (por `created_at`), `outbox_events`/`processed_events` (por tempo, com expurgo por DROP de partição já que não têm retenção de 10 anos), `audit_log`/`data_access_log` (por `created_at`; só após a cadeia de hash V45 ser compatível com partições e com retenção >= 10 anos).
