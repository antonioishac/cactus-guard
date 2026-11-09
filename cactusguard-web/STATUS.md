# CactusGuard Web — Status (2026-11-08, alinhamento aos contratos reais)

## Decisões de design (preservadas)
Sidebar escura `#0d3d36`, accent teal `#00c9a7`, tema light, responsivo; item ativo com texto escuro sobre o teal; botões primários `brand #00806b` (AA).

## Stack
React 19.3, Vite 8.3, TypeScript 6.0.3 (typescript-eslint só suporta `<6.1`), Tailwind 4.3, React Router 8.5, TanStack Query 5, RHF 7 + Zod 4, Vitest 5 + Testing Library, MSW 3, ESLint 10, openapi-typescript 7 (`overrides` no package.json: peer de TS 5 forçado para o TS 6).

## Fonte única dos contratos
- `src/api/openapi.json` (versionado) = `/v3/api-docs` do backend real. `src/api/generated/schema.d.ts` é gerado.
- Regenerar: subir o backend (`mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8083`), baixar `http://127.0.0.1:8083/v3/api-docs` para `src/api/openapi.json` e rodar `npm run api:types`.
- `npm run api:types:check` e o teste `src/api/contract.test.ts` falham se o schema gerado estiver desatualizado.
- `src/api/contract.ts` verifica em `tsc` que cada tipo de `src/api/types` é compatível com o DTO gerado (campo inexistente ou tipo incompatível = erro).
- MSW (`src/mocks`) espelha caminhos, corpos, status e erro `{code,message,traceId,fieldErrors}` + `X-Trace-Id`. Teste falha se uma requisição ficar sem handler.

## Real (alinhado ao backend)
Erro global; partes (filtros, PUT sem documento, reveal com motivo 10–500, PEP, recálculo por parte, KYC por parte); screening (review JSON, catálogo real); usuários (editar, reativar, reset de senha com exibição única); alertas (filtros, evidências, transações, histórico, comentários, escalar); cases (`POST /cases`, `slaStatus`, dossiê, notas, evidências, rejeição quatro olhos); SISCOAF (todos os status, histórico, aprovar, `fourEyesRequired`); regras (Page, métricas 30 d); fila `/approvals/pending` com `ownDecision/ownChange` e PEPs; auditoria global e log LGPD; dashboard completo; notificações (ler todas); relatórios (`errorMessage`). Tenant: `tenantName` exibido se vier no login.
- Code-splitting de todas as páginas: entrada 905 kB -> 42 kB (+ vendors em chunks); recharts só no chunk do dashboard.

## Ainda falta no backend
1. `slaStatus`/estado do prazo em alerta (seleção) e SISCOAF (D+1): `lib/deadline.ts` segue só nesses dois (classificação visual provisória).
2. `/dashboard/operational` e `/dashboard/metrics` sem DTO no OpenAPI (mapa genérico): tipos não verificados.
3. Entities ainda serializadas: endereços (`PartyAddress.party`), histórico de risco (`RiskHistory.party/changedBy`), notificações (`user`), watchlist (`addedBy`).
4. Watchlist: sem paginação, escrita via `@RequestParam`, sem edição.
5. `riskFactors` não é devolvido (fatores só no motivo do histórico).
6. Reset de senha: sem flag "trocar senha no próximo login".
7. Claim/campo de tenant no login (multi-tenancy) ainda não existe no OpenAPI.

## Pendências do front
Adicionar/remover endereço, relacionamento e UBO pela UI; histórico de versões cadastrais; e2e; cookie httpOnly para o refresh token.
