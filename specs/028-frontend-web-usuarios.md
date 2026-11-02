# Spec 028 — Frontend Web: Gestão de Usuários e Roles

## Objetivo
Interface completa de manutenção de usuários, roles e permissões.

## Funcionalidades
- Listagem e busca de usuários (filtros por status, role, tenant).
- Criação, edição e desativação/reativação de usuários.
- Associação de usuários a tenants e roles.
- Gestão de roles e permissões (criação/edição quando permitido).
- Visualização de histórico de alterações via auditoria.
- Reset de senha (disparo de fluxo do backend).

## Integração
- Consome User, Role, Permission e auditoria.
- Protegido por roles administrativas.

## Regras de tela e UX
- Validação de formulário (campos obrigatórios, formato de e-mail, senha forte quando aplicável).
- Confirmação modal em desativação.
- Feedback claro de sucesso e erro.
- Campos desabilitados conforme permissão.
- Indicação visual de status do usuário.
