# Spec 031 — Frontend Web: Dashboard

## Objetivo
Visão consolidada para o analista e supervisor ao entrar no sistema.

## Funcionalidades
- Cards de resumo (alertas abertos, cases em andamento, SLA vencendo, PEPs ativos).
- Lista resumida de alertas críticos e cases prioritários.
- Gráficos simples de volume (quando o backend fornecer dados agregados).
- Atalhos para os principais módulos.
- Filtros por período (quando aplicável).

## Integração
- Consome endpoints de dashboard/resumo do backend.
- Não calcula indicadores no front.

## Regras de tela e UX
- Loading skeleton nos cards.
- Atualização periódica ou manual.
- Links diretos para as listagens filtradas.
