# Spec 022 — Frontend Web: Autenticação e Segurança

## Objetivo
Fluxo completo e robusto de autenticação e autorização no Web.

## Fluxos detalhados

### Login
- Campos: identificador (e-mail ou usuário) + senha.
- Validação de formulário no cliente.
- Chamada ao endpoint de login.
- Em sucesso: armazena access + refresh token no `authStore` e em storage seguro.
- Redireciona para a rota de origem ou dashboard.
- Em falha: mensagem clara (credenciais inválidas, usuário bloqueado, etc.).

### Refresh automático
- Interceptor Axios captura 401.
- Se houver refresh token válido, chama endpoint de refresh.
- Em sucesso: atualiza tokens e repete a requisição original (com fila para evitar múltiplos refreshes simultâneos).
- Em falha: executa logout e redireciona para login com mensagem de sessão expirada.

### Proteção de rotas
- `ProtectedRoute` verifica:
  - Token presente e não expirado.
  - Roles/permissões necessárias para a rota.
- Redireciona para `/login` se não autenticado.
- Redireciona para `/unauthorized` se role insuficiente.

### Logout
- Chama endpoint de logout (revoga refresh token).
- Limpa `authStore` e storage.
- Redireciona para login.

## Estado (Zustand — authStore)
- `user`, `accessToken`, `refreshToken`, `roles`, `permissions`, `isAuthenticated`
- Ações: `login`, `logout`, `setTokens`, `clear`

## Regras de tela
- Menus e botões exibidos/ocultados ou desabilitados conforme permissões.
- Indicador visual durante refresh.
- Mensagens claras de sessão expirada e acesso negado.
- Bloqueio de ações enquanto o refresh está em andamento.
