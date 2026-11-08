# CactusGuard — PLD/FT SaaS

Plataforma SaaS de Prevenção à Lavagem de Dinheiro e Financiamento do Terrorismo para instituições reguladas pelo BCB (bancos, IPs, fintechs). Pacote `br.com.cactus.guard`.

## Stack e comandos
- Backend `cactusguard-backend/`: Java 25, Spring Boot 4.x, PostgreSQL, Redis, Flyway, Resilience4j, JWT. Detalhes: `cactusguard-backend/CLAUDE.md`.
  - Testes: `cd cactusguard-backend && JAVA_HOME=/c/Java/jdk-25.0.2 mvn test`
  - Um teste: `JAVA_HOME=/c/Java/jdk-25.0.2 mvn test -Dtest=NomeDoTest`
- Web `cactusguard-web/`: React 18 + Vite + Tailwind + Zustand. `npm run dev` | `npm run build` (inclui `tsc`).

## Invariantes regulatórias (nunca violar)
- Decisão de case é humana: `COMMUNICATE` ou `ARCHIVE`, com justificativa obrigatória. Escalar não é decisão.
- Quatro olhos em PEP / alto risco / EDD: aprovador ≠ quem decidiu.
- Prazos: seleção ≤ 45 dias da ocorrência; análise ≤ 45 dias da seleção; comunicação COAF até D+1 útil da decisão.
- Espécie ≥ R$ 50 mil (inclui provisionamento de saque): comunicação automática, sem análise.
- Hit CSNU confirmado: bloqueio imediato (Lei 13.810).
- Nunca dar ciência ao cliente (tipping-off): case/comunicação jamais expostos em canal do cliente.
- AuditLog append-only; toda ação relevante audita. Retenção mínima 10 anos.
- PII criptografada em repouso; busca só por hash; acesso a PII registrado (LGPD).
- Na dúvida regulatória: interpretação mais conservadora + registrar na spec.

## Qual spec ler (índice completo: `specs/README.md`)
| Tarefa | Spec |
|---|---|
| Prazos, COAF, retenção, CSNU | specs/003 |
| Campos de entidade / migration nova | specs/046, specs/002 |
| Regras e cenários | specs/008, 009, 013 |
| Case, dossiê, aprovação | specs/012, 033 |
| PEP / KYC / UBO | specs/017, 010 |
| Screening | specs/011, 037 |
| Padrões de código backend | specs/047, 007 |
| Criptografia / LGPD | specs/040 |
| Endpoints | specs/033 |
| Frontend | `cactusguard-web/CLAUDE.md` |

O que ainda não está implementado: `specs/README.md` → "Status de implementação".

## Regras de trabalho
- Não ler `target/`, `node_modules/`, `dist/`, `.idea/`, logs.
- Ler a spec da tarefa, não a pasta inteira.
- Após alterar código, rodar os testes do módulo afetado (ex.: `-Dtest='Case*Test'`); suíte completa antes de concluir.
- Exit code: não confiar em `cmd | tail`; use `cmd; echo EXIT=$?` ou `set -o pipefail`.
- Specs novas: próximo número livre em `specs/README.md`; atualizar o índice.

## Agentes e skills do projeto
Ver `.claude/README.md`.
