# Spec 023 — Frontend Web: Módulo de Alertas

## Objetivo
Experiência completa de gestão de alertas para o analista.

## Funcionalidades
- Listagem paginada com filtros (tipo, severidade, status, período, responsável, parte).
- Ordenação por colunas relevantes.
- Indicadores visuais de SLA (cores e contagem regressiva quando disponível).
- Detalhe do alerta com contexto completo (parte, regra, transações relacionadas, histórico).
- Ações conforme permissão: assumir, escalar, criar case, marcar falso positivo, comentar.
- Busca rápida.
- Atualização via polling ou refetch após ações.

## Integração
- Consome listagem, detalhe e endpoints de ação do serviço de alertas.
- Todas as ações disparam chamadas reais e atualizam a lista.

## Regras de tela e UX
- Loading skeleton na listagem e no detalhe.
- Estado vazio com mensagem e ação sugerida.
- Confirmação modal em ações irreversíveis.
- Toasts de sucesso e erro.
- Botões desabilitados quando o usuário não tem permissão.
- Destaque visual para alertas críticos e SLA vencendo.
