---
name: nova-migration
description: Cria uma nova migration Flyway (V<n>__descricao.sql) no cactusguard-backend para mudança de schema, índice, seed ou correção de migration anterior. Use sempre que precisar alterar o banco — migrations existentes nunca são editadas.
argument-hint: "<descricao_snake_case> [o que muda]"
---

Objetivo: $ARGUMENTS

1. Próximo número: `ls cactusguard-backend/src/main/resources/db/migration | sed -nE 's/^V([0-9]+)__.*/\1/p' | sort -n | tail -1` → use +1. Confira `git status` para não colidir com migration criada em paralelo.
2. Nunca edite `V*.sql` existente (hook bloqueia arquivo rastreado pelo git). Para corrigir uma anterior, a nova migration faz o `ALTER`/ajuste.
3. Crie com Write: `cactusguard-backend/src/main/resources/db/migration/V<n>__<descricao_snake_case>.sql`.
4. Checklist do SQL:
   - [ ] PostgreSQL puro; `IF NOT EXISTS`/`ON CONFLICT DO NOTHING` quando fizer sentido
   - [ ] índices nas colunas de filtro/ordenação/FK; GIN para `tsvector`
   - [ ] PII cifrada (`TEXT`) + `*_hash VARCHAR(64)` indexado; nada em claro
   - [ ] sem `ON DELETE CASCADE` em dados com retenção de 10 anos; `audit_log` continua append-only
   - [ ] regra de monitoramento: nova versão, não UPDATE destrutivo
   - [ ] coluna `NOT NULL` nova em tabela com dados: default ou backfill no mesmo arquivo
5. Atualize a entity correspondente (`@Column` com mesmo nome/tipo/nullability/tamanho).
6. Testes rodam em H2 sem Flyway — eles NÃO validam esta migration. Se houver Postgres local, suba a app com `ddl-auto=validate`; senão, delegue revisão ao subagente `flyway-migration-guardian`.
