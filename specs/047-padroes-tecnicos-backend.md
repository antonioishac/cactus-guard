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
- **Eventos de domínio**: mudanças relevantes publicadas como eventos.  
  **Kafka é a decisão de arquitetura** (não apenas Spring ApplicationEvent). Status: **pendente** — o código atual não publica eventos em Kafka.  
  Requisitos obrigatórios:
  - DLQ em todo consumer.
  - Retentativas com backoff.
  - Controle e reprocessamento de mensagens em DLQ (com motivo do erro registrado).
  - Nunca descartar mensagem silenciosamente.

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
  - **Riscos residuais**: FKs entre tabelas não incluem `tenant_id` (a FK ignora RLS; mitigação: UUIDs não adivinháveis + filtro/RLS nas escritas); SQL injection poderia chamar `set_config('app.tenant_id', …)` (prevenção: sem SQL dinâmico com entrada externa); `document_hash` usa o mesmo pepper em todos os tenants (pepper por tenant é evolução); cadeia de hash do `audit_log` ainda global; `high_risk_jurisdictions` de origem `INTERNAL` ainda é global; troca de senha temporária no primeiro acesso ainda não é forçada.

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
