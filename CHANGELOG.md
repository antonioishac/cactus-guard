# Changelog

Formato: [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) · Versionamento: [SemVer](https://semver.org/lang/pt-BR/).
Backend, web e mobile compartilham o mesmo número de versão. Roadmap e fases: `specs/048`.

## [0.5.0] — 2026-11-09 — Fundação de plataforma (beta interna)

Fases 0 (estabilização) e 1 (fundação de plataforma) da spec 048. **Não é versão comercial** — ver limitações em
`docs/releases/v0.5.0.md`. Banco de dados na migration **V50**.

### Adicionado
- **Multi-tenancy** com isolamento em duas camadas: `@TenantId` (Hibernate) + Row Level Security `FORCE` no PostgreSQL;
  FKs compostas `(tenant_id, id)`; hash de documento com chave derivada por tenant; provisionamento de tenants
  (`PLATFORM_ADMIN`). Prova automatizada (`TenantIsolationIT`, `TenantHardeningIT`).
- **Eventos de domínio** com outbox transacional + Kafka (5 tópicos + DLQ), consumidores idempotentes, DLQ com
  reprocessamento/descarte auditado; monitoramento de transações em segundos (antes: batch de 6 h).
- **Regulatório**: decisão de case `COMMUNICATE | ARCHIVE` com justificativa; quatro olhos (aprovação e rejeição);
  prazos 45 + 45 dias + D+1 útil com calendário de feriados; COE automática para espécie ≥ R$ 50 mil;
  bloqueio imediato de bens em hit CSNU (Lei 13.810); declaração negativa; PEP com expiração de 5 anos e aprovação da
  alta administração; risco multifator; governança de regras com versionamento e aprovação.
- **Dossiê de case** consolidado, notas, evidências com SHA-256, número legível `CG-AAAA-NNNNNN`.
- **Segurança/LGPD**: autorização por perfil em todos os endpoints (teste de cobertura), perfil AUDITOR, criptografia
  AES-256-GCM com keyring versionado, revelação de PII com motivo registrado, retenção mínima de 10 anos,
  bloqueio progressivo de login + rate limit por IP/usuário/tenant, IP real só via proxies confiáveis.
- **Auditoria** append-only com cadeia de hash por tenant, verificação diária e âncoras horárias.
- **Observabilidade**: OpenTelemetry ponta a ponta (HTTP → outbox → Kafka → consumidor), logs JSON, SLOs (spec 038).
- **Escala**: `transactions` particionada por mês com criação automática de partições (retenção ≥ 10 anos).
- **CI/CD**: workflows de backend, web, mobile, segurança (CodeQL, gitleaks, Trivy, dependency review) e imagem
  Docker com SBOM e proveniência; Dependabot; `SECURITY.md`.
- **Web**: React 19 / Vite 8 / Tailwind 4; todas as telas de compliance; tipos gerados do OpenAPI real.
- **Mobile**: fundação Flutter (login, segurança de dispositivo, biometria, deep links, push preparado sem Firebase).
- **Push** no backend (registro de dispositivos, payload sem PII), envio real desligado até existir projeto Firebase.
- **Specs**: 048 (estratégia competitiva e roadmap PLD + FT + Fraude) e 049 (desenho do motor de decisão unificado —
  aprovado; implementação na Fase 2).

### Alterado
- Java 25 + Spring Boot 4.1.2 (antes Java 21 / Spring Boot 3.3). Dev sobe com zero configuração (perfil `dev` padrão,
  provedores externos sempre simulados).
- Usuários de desenvolvimento via `db/dev/R__dev_users.sql` (classe `DataInitializer` removida).

### Corrigido
- Backend não compilava; migrations V15–V20 quebravam em banco limpo; criptografia falhava (chave de tamanho inválido);
  testes nunca rodavam de fato (o build mascarava a falha). Mais de 30 defeitos — detalhes no histórico do git.

### Segurança
- Repositório deve ser **privado**; segredo JWT padrão antigo presente no histórico (commit `12a3af1`) — rotacionar se
  algum ambiente subiu sem `JWT_SECRET`.
