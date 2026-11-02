# Spec 025 — Frontend Web: Cases e Dossiê

## Objetivo
Gestão completa de cases e construção do dossiê, consumindo integralmente os serviços do backend.

## Funcionalidades
- Listagem paginada de cases com filtros (status, responsável, SLA, risco, período, parte).
- Abertura de case a partir de alerta (com pré-preenchimento).
- Tela de dossiê com seções:
  - Dados da parte e UBO
  - Histórico de screening e status PEP
  - Transações relacionadas
  - Justificativas e comentários
  - Anexos (upload e download)
  - Linha do tempo de eventos
- Ações: assumir, escalar, solicitar EDD, decidir (com justificativa obrigatória), fechar.
- Indicadores visuais de SLA e status.

## Integração
- Consome Case, Dossiê, Anexos, Decisões e histórico.
- Upload de anexos via endpoint dedicado.
- Decisão envia payload completo para o backend validar.

## Regras de tela e UX
- Justificativa obrigatória e com tamanho mínimo em decisões.
- Confirmação modal em decisões e fechamento.
- Loading e empty states em todas as seções.
- Botões condicionados a roles e ao status atual do case.
- Feedback claro após cada ação.
