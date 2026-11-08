# Backend — CactusGuard

Padrões completos: `specs/047-padroes-tecnicos-backend.md`. Endpoints: `specs/033-contratos-api.md`.

## Pacotes (`src/main/java/br/com/cactus/guard/`)
- Módulo convencional: `<modulo>/{controller,service,entity,dto,repository}` (+ `mapper/` ao adotar MapStruct no módulo) (alert, case_, kyc, party, report, risk, transaction, user, watchlist…).
- Integrações externas: `integration/{kyc,screening,siscoaf}` — `*ProviderPort` + provider real + `Mock*Provider` + `*Orchestrator` (Resilience4j).
- Motor de regras: `rule/engine`. Transversais: `common` (exceções, DTOs), `config` (security, JWT, rate limit, Redis), `crypto`, `audit`, `job`.
- Testes espelham os pacotes em `src/test/java/...`; fluxos ponta a ponta em `e2e/` e `flow/`.

## Convenções
- DTO novo: `record` (legado usa Lombok `@Data`). Mapeamento entidade↔DTO via MapStruct.
- Listagens sempre paginadas (`Pageable`); busca textual via full-text (`tsvector` + GIN), não `LIKE`.
- Flyway em `src/main/resources/db/migration`: **nunca editar migration já aplicada** — criar `V<n+1>__descricao.sql`. `ddl-auto=validate`: entidade e migration mudam juntas.
- PII: `@Convert(converter = EncryptedStringConverter.class)` + coluna `*_hash` para busca (ex.: `document_hash`). Nunca logar PII em claro.
- Leitura de PII: registrar via `DataAccessService` (LGPD).
- Ação relevante (alerta, decisão, comunicação, regra, PEP, risco): registrar via `AuditService`. AuditLog é append-only.
- Chamada externa: sempre atrás de Port + circuit breaker; nunca acoplar controller/service a provider concreto.
- Erros: lançar `BusinessException` (422) etc.; tratamento central em `common`.

## Testes
- Todos: `JAVA_HOME=/c/Java/jdk-25.0.2 mvn test`
- Um: `JAVA_HOME=/c/Java/jdk-25.0.2 mvn test -Dtest=CaseEscalateTest` (método: `-Dtest=Classe#metodo`).
- Verifique o exit code (`echo EXIT=$?`), não só o final da saída.

## Armadilhas
- Perfil `test` usa H2 (`MODE=PostgreSQL`, `ddl-auto=create-drop`, Flyway desligado): testes **não validam migrations** nem SQL nativo/`tsvector`. Mudou migration ou query nativa → validar contra PostgreSQL (Testcontainers, se configurado, ou banco local).
- Relatórios PDF/Excel são gravados em `reports/` relativo ao diretório de execução (ignorado no git).
- `case_` tem underscore (palavra reservada).
