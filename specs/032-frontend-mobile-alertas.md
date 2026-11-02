# Spec 032 — Frontend Mobile: Alertas e Notificações

## Objetivo
Experiência móvel focada em alertas, ações rápidas e notificações.

## Funcionalidades
- Listagem de alertas com filtros básicos (status, severidade).
- Detalhe do alerta.
- Ações rápidas: assumir, comentar, criar case (conforme permissão e backend).
- Notificações push para alertas críticos e SLA.
- Deep link da notificação para o detalhe do alerta/case.
- Indicador de não lidas.

## Integração
- Consome os mesmos endpoints de alertas do backend.
- FCM para push.

## Regras de tela
- Interface simples e rápida.
- Confirmação em ações.
- Estados de loading e empty.
- Feedback de sucesso/erro.
