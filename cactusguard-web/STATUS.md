# CactusGuard Web — Status (2026-11-08, Frontend Etapa 1)

## Decisões de design (aprovadas, preservadas)
Sidebar escura `#0d3d36`, accent teal `#00c9a7`, menu hambúrguer colapsável, tema light, responsivo. Ajuste de acessibilidade: item ativo do menu usa texto escuro sobre o teal e botões primários usam `brand #00806b` (contraste AA com texto branco).

## Stack (versões instaladas)
React 19.3, Vite 8.3, **TypeScript 6.0.3** (a 7.0.2 é a última, mas `typescript-eslint` 8.72 só suporta `<6.1`; subir quando houver suporte), Tailwind 4.3 (`@tailwindcss/vite`, tokens em `@theme`), React Router 8.5 (data router), Recharts 3.10, Zustand 5.1, Axios 1.21, TanStack Query 5, RHF 7 + Zod 4, Vitest 5 + Testing Library + jsdom, MSW 3, ESLint 10 (flat) + typescript-eslint + react-hooks.

## Entregue
- Auth: login, store, refresh com fila única, rota protegida, guarda por role, ações por role, timeout por inatividade.
- Layout: AppLayout/Sidebar responsivos, menu por role, breadcrumbs, componentes base (`src/components/ui`).
- Dashboard (KPIs, gráficos, SLA de cases, alertas recentes/críticos), Alertas (lista + detalhe + ações), Cases (lista + dossiê + decidir/escalar/aprovar, PENDING_APPROVAL).
- MSW cobrindo todos os endpoints usados; 35 testes.

## Divergências spec x backend (afetam o front)
1. Erro do backend = `{timestamp,status,error,message}`; sem `code`/`traceId` (o front já lê se vierem).
2. `AuthResponse.user` sem `permissions`; tem `username`. Logout exige `refreshToken` no corpo (spec: header).
3. Alertas: sem `escalate`; `GET /alerts` só filtra `status`/`severity` (spec: type, assignedTo, from, to, partyId); detalhe sem evidências/transações vinculadas/histórico; `partyDocument` ausente.
4. Cases: abrir = `POST /cases/from-alert/{id}` (spec: `POST /cases`); `GET /cases` só filtra `status`; `CaseResponse` sem `decidedById` (UI não consegue ocultar Aprovar ao decisor; backend bloqueia), sem `slaStatus`/`caseNumber`/`indicatorCodes`; escalar usa `justification`+`escalatedToId` (spec: `reason`+`targetRole`); aprovar usa `{comment}` e não tem rejeição (spec: `{approved,justification}`); status reais: OPEN, ESCALATED, PENDING_APPROVAL, COMMUNICATE, ARCHIVED (spec: IN_ANALYSIS, CLOSED).
5. Sem endpoints de dossiê, notas, evidências (upload/download de case) nem revelar PII (`POST /parties/{id}/reveal` é proposta do front).
6. Dashboard: só `/dashboard/operational`; spec 045 prevê `metrics`, `alerts-chart`, `severity-distribution`; `case-sla` é proposta (seções degradam).
7. `PartyResponse` não expõe os campos da spec 046 (mãe, Receita, CNAE, renda, origem, PEP cargo/datas, UBO...); o front os exibe se vierem. Risk-history, UBO, audit retornam entities sem DTO. `GET /parties` sem filtros da spec 033 (`/search?q=` existe).
8. Spec 021 cita MUI; spec 043 e a decisão aprovada usam Tailwind (mantido Tailwind).

## Etapa 2 (pendente)
Partes/KYC/PEP (026), Screening, SISCOAF (030), Regras (029; backend já tem versões/aprovação), Relatórios (027), Usuários (028), Auditoria; fila de aprovação/rejeição de case; notificações (sino do topo); code-splitting (bundle ~1 MB); testes e2e; cookie httpOnly p/ refresh token; trocar `lib/deadline.ts` pelo SLA do backend.

## Etapa 2A: Partes/KYC, Screening, Usuários (2026-11-08)
**Entregue**: Partes (lista com busca/filtros e paginação no servidor; ficha 360º com abas Cadastro+Receita, Endereços, Relacionamentos, UBO, PEP, Risco, Transações, Screening, KYC; faixa CSNU; cadastro/edição RHF+Zod com CPF/CNPJ validado), Screening (fila de hits, execução manual, catálogo de listas, lista interna), Usuários (CRUD só ADMIN, descrição dos perfis). MSW em `src/mocks/handlers/pld.ts` + `src/mocks/pldData.ts`.
**Divergências 2A (faltam no backend)**:
1. **Crítico**: `PUT /parties/{id}` sobrescreve `document`/`email`/`phone` com o enviado; a UI só conhece o valor mascarado. O backend deve ignorar valores com `*` (e `document` não pode ser `@NotBlank` na edição).
2. `PartyRequest`/`PartyResponse` sem os campos da spec 046 (mãe, renda, CNAE, PEP, Receita, bloqueio CSNU, `sourceType`, `nextReviewDate`, fatores de risco, aprovação PEP com nome); o front envia/exibe se existirem. Sem GET de PEP (`PepResponse` só no POST).
3. `GET /parties` sem filtros `type`, `riskLevel`, `pep`, `sourceType`, `document` (só `/search?q=`); `GET /kyc` sem `partyId` (o cliente refiltra); sem recálculo de risco por parte (`POST /parties/{id}/risk/recalculate` é proposta; hoje só `/risk/recalculate` global, ADMIN).
4. Screening: controller de revisão só lê `status` (o service já aceita justificativa; o front envia `status`+`justification` em corpo form); sem `partyName`/`requiresAssetFreeze`/justificativa no `ScreeningResultResponse`; sem catálogo `GET /screening/lists` (UI usa catálogo ilustrativo); códigos de lista aceitos dependem do seed.
5. Watchlist: sem paginação, `POST` usa `@RequestParam` (front envia como form no corpo); sem edição.
6. Usuários: faltam `PUT /users/{id}` (nome, e-mail, roles), reativação e reset de senha; sem filtros de status/role.
7. Entities serializadas direto (UBO, endereços, relacionamentos, histórico de risco): UBO precisa expor `ownerParty` (id/nome) e `ownershipChain`; `relatedParty` hoje seria a entity completa (risco de vazar PII).
8. `Transaction` response sem `paymentMethod`/`counterpartyCountry`; direção real é `IN|OUT`.
**Pendências**: bundle ainda grande (code-splitting), histórico de versões cadastrais, ações de EDD além do indicador, transações de contraparte no screening, adicionar/remover endereço, relacionamento e UBO pela UI (hoje só leitura).

## Etapa 2B: SISCOAF, Regras, Fila de aprovação, Relatórios, Auditoria, Notificações (2026-11-08)
**Entregue**: SISCOAF (lista por status com semáforo de prazo D+1 útil, detalhe com payload legível/indícios/histórico, aprovar/enviar/cancelar/retificar via `JustificationDialog` só para SUPERVISOR, declaração negativa por ano mostrando o bloqueio do backend, aviso fixo de sigilo e destaque de COE automática); Regras (lista com filtros, detalhe com parâmetros/métricas/histórico e diff de versões, criar/editar com editor de parâmetros gerado do JSON, aprovar com bloqueio do criador, ativar/desativar, executar motor); Fila de aprovação (cases PENDING_APPROVAL + regras pendentes, links para dossiê/regra); Relatórios (catálogo, geração PDF/Excel com período, polling até COMPLETED/FAILED, "meus relatórios", download); Auditoria (trilha com diff old/new e log LGPD por entidade/usuário); Notificações (sino no header, contador com polling de 30 s, marcar como lida, link). Rotas 2B em `src/app/routes2b.tsx` (lazy). MSW em `src/mocks/handlers/{siscoaf,rules,reports,audit,notifications}.ts` + `db2b.ts`.
**Divergências 2B (backend)**:
1. SISCOAF: `GET /siscoaf` lista UM status por vez (sem "todos") e não filtra por tipo/período/parte; `SiscoafResponse` sem histórico de status, motivo de cancelamento, indícios (`indicatorCodes`), nome da parte e período; sem endpoint de aprovação (`POST /siscoaf/{id}/approve` é proposta; `submit` aceita DRAFT direto); `submit` ignora a justificativa enviada.
2. Regras: `GET /rules` devolve `List` só de ATIVAS (sem pendentes/inativas, sem paginação, sem `GET /rules/{code}`); `includeInactive=true` é proposta; `MonitoringRuleResponse` sem `createdById` (quatro olhos só é desabilitado na UI se o campo vier), `carta4001Reference`, `appliesToRiskLevels`, métricas (`alertsGenerated`, `falsePositiveRate`); `RuleVersionResponse` sem criador. Não há "enviar para aprovação" separado: criar/editar já vai para PENDING_APPROVAL e "ativar" é `toggle?active=true`.
3. Auditoria: só consulta por entidade (`/audit/entity/{tipo}/{id}`); sem busca global por usuário/ação/período (a UI filtra a página recebida). `AuditLog` serializado como entity.
4. Relatórios: `ReportJobResponse` sem mensagem de erro; catálogo traz `active` (UI esconde inativos); parâmetros são livres (UI envia `from`/`to`).
5. Notificações: entity serializada; sem "marcar todas como lidas".
6. Aprovação de PEP pendente: sem endpoint de listagem (card informativo na fila).
**Pendências**: code-splitting das páginas da 2A; mais testes de tela (fila de aprovação, sino); histórico de status e payload editável de retificação dependem do backend.
