---
paths:
  - "cactusguard-backend/src/main/resources/db/migration/**"
  - "cactusguard-backend/src/main/java/**/entity/**"
---

# Migrations Flyway e entities

- Migration rastreada pelo git = aplicada = IMUTÁVEL. Ajuste sempre em nova `V<n+1>__descricao.sql` (skill `/nova-migration`). O hook `protect_migrations.py` bloqueia a edição.
- Mudou `@Column`/`@Table` na entity → precisa de migration correspondente (nome, tipo, nullability, tamanho).
- Índices em colunas de filtro/ordenação/FK desde a criação; GIN em `tsvector`.
- PII: coluna cifrada + `*_hash` indexado. `audit_log` append-only. Sem `ON DELETE CASCADE` em dados com retenção de 10 anos.
- Testes usam H2 sem Flyway: não validam o SQL. Revisão: subagente `flyway-migration-guardian`.
