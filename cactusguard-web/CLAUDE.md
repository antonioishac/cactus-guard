# Web — CactusGuard

React 19 + TypeScript 6 + Vite 8 + Tailwind 4 + React Router 8 (data router) + TanStack Query + React Hook Form/Zod + Zustand + Axios + Recharts. UI e mensagens em pt-BR.

## Comandos
`npm run dev` | `npm run dev:mock` (MSW, sem backend; logins `analista|supervisor|supervisor2|auditor|admin`, senha `senha123`) | `npm run typecheck` | `npm run lint` | `npm test -- --run` | `npm run build` | `npm run api:types` (regenera tipos do `src/api/openapi.json`) | `npm run api:types:check`.
Env: ver `.env.example` (`VITE_API_URL` com prefixo `/api/v1`, `VITE_USE_MOCK`, `VITE_IDLE_TIMEOUT_MIN`).

## Padrão de módulo (replicar na etapa 2)
```
src/api/types/<modulo>.ts   tipos à mão, verificados contra o schema gerado em src/api/contract.ts (inclua o novo DTO lá)
src/api/<modulo>.ts         funções axios puras (sem React); contrato = OpenAPI real (src/api/openapi.json)
src/features/<modulo>/hooks.ts        useQuery/useMutation (invalidate no onSuccess; toast de sucesso via meta.successMessage)
src/features/<modulo>/pages|components
src/mocks/handlers.ts + data.ts       handler MSW do módulo (usado em dev:mock e nos testes)
src/app/nav.ts + router.tsx           1 item de menu (roles) + 1 rota (`handle.crumb`)
```
Regras: listas via `DataTable` + `useListState` (filtros/página/ordem na URL, servidor pagina; nunca PII na URL); ações regulatórias via `JustificationDialog` (justificativa >= 20 não brancos + confirmação); botões por `useAuth().can('<ação>')` (`src/lib/permissions.ts`, espelha @PreAuthorize); PII via `MaskedValue`; prazos de case via `SlaBadge` (slaStatus do backend; o front só exibe); `ErrorState` mostra a mensagem do contrato + traceId (403 vira aviso). Nada de `console.*` (lint), `any` ou cor fora dos tokens de `styles/index.css`.

## Segurança
- Access token só em memória; refresh token em memória + sessionStorage (trade-off: sobrevive a reload, exposto a XSS; ideal = cookie httpOnly do backend). Nada de PII em storage/URL/log.
- Refresh em 401 com fila única (`src/api/client.ts`); falha = logout. Sessão expira por inatividade (padrão 15 min).
- Tipping-off: case, alerta e SISCOAF só em telas internas de PLD; nada voltado ao cliente final.
- `src/lib/deadline.ts` é PROVISÓRIO e só resta em alerta (seleção) e SISCOAF, até o backend devolver o estado do prazo.
- Testes: `src/test/setup.ts` reseta mocks/stores/storage a cada teste e falha em requisição sem handler MSW.

## Referências
Estado e pendências: `STATUS.md`. Specs: `../specs/021`–`033`, `035`, `046`; `../specs-frontend/043`–`045`.
