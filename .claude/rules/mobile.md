---
paths:
  - "cactusguard-mobile/**/*.dart"
  - "cactusguard-mobile/pubspec.yaml"
  - "cactusguard-mobile/android/**"
---

# Mobile (Flutter + Riverpod + Dio + go_router)

- Specs: 024 (escopo, decisões, **segurança mobile**), 032 (alertas/notificações), 033 (contratos), 035 (identidade). Padrão de pastas: `cactusguard-mobile/CLAUDE.md`.
- Sem regra de negócio no app: prazos, quatro olhos e permissões vêm do backend; UI por role é só conveniência.
- Tokens só em `flutter_secure_storage`; `FLAG_SECURE` ligado; PII mascarada; nada sensível em log/cache.
- Push: payload só `{type, id}` com texto genérico — nunca nome/documento/valor (tipping-off).
- Ações regulatórias pedem confirmação + justificativa (≥ 20 caracteres).
- Verificação: `cd cactusguard-mobile && flutter analyze && flutter test` (cheque o EXIT de cada um).
