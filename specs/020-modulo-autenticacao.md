# Spec 020 — Módulo de Autenticação e Autorização (CactusGuard)

## Objetivo
Fornecer um módulo completo de autenticação, autorização e gestão de usuários para o MVP do CactusGuard.  
Todo acesso ao sistema passa por este módulo. Cada módulo de negócio recebe e valida o JWT e aplica as regras de permissão.

## Escopo do MVP

### 1. Gestão de Usuários
- Criação, edição, desativação e reativação de usuários.
- Associação de usuário a um ou mais tenants.
- Reset de senha (fluxo interno no MVP).
- Bloqueio após tentativas inválidas.
- Histórico de alterações (via AuditLog).

### 2. Roles e Permissões (RBAC)
- Roles configuráveis por tenant (ex.: ANALYST, SUPERVISOR, ADMIN, AUDITOR).
- Permissões granulares por recurso e ação (ex.: `alert:read`, `case:decide`, `rule:approve`, `report:export`).
- Atribuição de roles a usuários.
- Verificação de permissão em cada endpoint protegido.

### 3. Autenticação e JWT
- Login com usuário e senha (MVP).
- Emissão de access token (JWT) + refresh token.
- JWT contém: `sub`, `tenant_id`, `roles`, `permissions` (ou roles que são resolvidas), `exp`, `iss`.
- Backend atua como OAuth2 Resource Server em todos os módulos.
- Validação do JWT em cada requisição (assinatura, expiração, tenant).
- Logout / invalidação de refresh token.

### 4. Propagação e Controle entre Módulos
- Todos os módulos recebem o JWT via header `Authorization: Bearer`.
- Cada módulo valida o JWT e extrai `tenant_id` + roles/permissions.
- Filtro Hibernate de multi-tenancy habilitado a partir do claim do JWT.
- Anotações de segurança (`@PreAuthorize`) ou verificações equivalentes nos controllers e services críticos.
- Sem chamada entre módulos sem propagação do contexto de segurança (quando aplicável).

### 5. Segurança e Auditoria
- Senhas armazenadas com hash forte (bcrypt ou argon2).
- Rate limiting em endpoints de login.
- Auditoria de: login (sucesso/falha), criação/alteração de usuário, alteração de role, tentativas de acesso não autorizado.
- Logs estruturados com trace id.
- Preparação arquitetural para autenticação via IdP externo (Azure AD / OIDC) no futuro — o fluxo interno deve ser substituível por adapter.

## O que fica fora do MVP
- Login social ou IdP externo ativo (Azure AD fica preparado, mas não implementado).
- MFA (pode ser adicionado depois).
- Gestão avançada de sessões ou SSO completo.

## Pacote e Estrutura
- Pacote: `br.com.cactus.guard.auth`
- Organização convencional (controller → service → repository) ou hexagonal leve se a integração com IdP externo for modelada como port desde o início.
- Entidades principais: `User`, `Role`, `Permission`, `UserTenant`, `RefreshToken`.

## Integração com o restante do sistema
- O módulo `common` fornece o filtro de tenant e o tratamento de erros de segurança.
- Todos os outros módulos dependem apenas da validação do JWT e das anotações de segurança.
- Eventos de domínio (Kafka) para ações críticas de usuário (criação, desativação, mudança de role) podem ser publicados para auditoria centralizada.

## Critérios de Pronto
- Usuário pode ser criado, associado a tenant e roles, e fazer login.
- JWT é emitido e validado em qualquer módulo.
- Endpoints protegidos rejeitam acesso sem permissão adequada.
- Multi-tenancy funciona a partir do claim do JWT.
- Todas as ações relevantes geram auditoria.
- Documentação OpenAPI inclui os endpoints de autenticação e os requisitos de segurança.
