# Spec 024 — Frontend Mobile (Flutter): Visão e Escopo

## Objetivo
App Flutter focado em operação móvel do analista e supervisor, consumindo os mesmos endpoints do backend.

## Escopo do MVP
- Login + Refresh automático de token (interceptor Dio).
- Listagem e detalhe de alertas (com filtros básicos).
- Ações rápidas em alertas e cases (assumir, aprovar, comentar — conforme permissão).
- Consulta de casos e status.
- Notificações push (FCM) para alertas críticos e SLA.
- Deep link a partir da notificação para o detalhe correspondente.

## Princípios
- Sem regras de negócio.
- Interface rápida e objetiva.
- Mesmo modelo de autenticação do Web.
- Tratamento de erros e estados de loading/empty.

## Stack recomendada
- Flutter + Dart
- Dio com interceptors (JWT + refresh)
- Riverpod (gerenciamento de estado)
- Firebase Cloud Messaging + flutter_local_notifications
- go_router (navegação e deep links)

## Identidade Visual
- Logo oficial CactusGuard (spec 035).
- Usar em splash screen, header e ícone do app.
- Configuração de regras
- Relatórios complexos e exportação
- Edição completa de dossiê
- Gestão de usuários
