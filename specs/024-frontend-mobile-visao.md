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

## Fora do escopo do MVP mobile
- Configuração de regras
- Relatórios complexos e exportação
- Edição completa de dossiê
- Gestão de usuários

## Decisões (2026-11-08)
- Pacote Android: `br.com.cactus.guard.mobile`. Alvo inicial: Android (iOS depois, exige macOS).
- Push (FCM): app e backend preparados com envio real **desligado** até existir projeto Firebase (`google-services.json` + credencial do servidor por variável de ambiente).

## Segurança mobile (obrigatório — PLD/LGPD)
- Tokens só em armazenamento seguro (Android Keystore via `flutter_secure_storage`); nunca em SharedPreferences.
- `FLAG_SECURE`: bloqueia screenshot, gravação de tela e preview no app switcher.
- Desbloqueio por biometria ao voltar ao app + timeout de sessão por inatividade.
- Push **sem PII nem nome de cliente**: payload só com tipo + id (ex.: `{"type":"ALERT","id":"…"}`); o app busca o detalhe autenticado. Evita vazamento em tela bloqueada e tipping-off (Lei 9.613, art. 11, II).
- PII sempre mascarada; nada de dado sensível em log ou cache persistente.
