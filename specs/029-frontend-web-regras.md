# Spec 029 — Frontend Web: Monitoramento e Regras

## Objetivo
Visualização e gestão controlada das regras de monitoramento.

## Funcionalidades
- Listagem de regras (ativas, inativas, pendentes de aprovação) com filtros.
- Detalhe da regra: condição, severidade, cenário, versão, histórico.
- Ativação, desativação e submissão para aprovação (conforme role).
- Visualização de alertas gerados pela regra.
- Comparação básica de versões (quando o backend fornecer).

## Integração
- Consome MonitoringRule e endpoints de aprovação.
- Não valida nem executa regras no front.

## Regras de tela e UX
- Diferenciação visual clara por status.
- Confirmação em alterações de status.
- Botões condicionados a roles (ex.: só aprovador pode aprovar).
- Loading e empty states.
