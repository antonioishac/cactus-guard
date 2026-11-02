# Spec 021 — Frontend Web: Visão Geral e Arquitetura

## Objetivo
Definir a arquitetura e os princípios do frontend Web do CactusGuard.

O frontend é um **cliente puro**: consome todos os serviços do backend, oferece a experiência completa ao usuário e **não contém regras de negócio**. Contém apenas regras de tela, validação de formulário no cliente, orquestração de chamadas e feedback visual.

## Stack
- React 18+ + TypeScript
- Vite
- Axios (interceptors centralizados)
- Zustand (estado de sessão e UI)
- TanStack Query (cache, sincronização e estados de servidor)
- MUI (Material UI)
- React Router v6
- React Hook Form + Zod (validação de formulários)

## Princípios obrigatórios
- Zero regra de negócio no front (risco, PEP, prazos regulatórios, decisões, montagem de payload SISCOAF etc. ficam no backend).
- Toda listagem é paginada e filtrada no backend.
- Proteção de rotas e de ações baseada em roles/permissões do JWT.
- Tratamento centralizado de erros de API (mensagens padronizadas).
- Estados de loading, empty, error e success em todas as telas.
- Feedback imediato em toda ação (toast/snackbar).
- Acessibilidade básica e responsividade.

## Estrutura de pastas
```
src/
├── api/
│   ├── axios.ts              # instance + interceptors
│   └── services/             # um arquivo por domínio
├── components/               # componentes reutilizáveis
├── features/                 # um feature por módulo de negócio
├── hooks/
├── layouts/
├── pages/
├── routes/
│   ├── ProtectedRoute.tsx
│   └── index.tsx
├── stores/
│   └── authStore.ts
├── types/
└── utils/
```

## Autenticação e Segurança (resumo)
- Login armazena tokens.
- Interceptor adiciona Bearer e faz refresh automático em 401.
- `ProtectedRoute` verifica autenticação + roles.
- Logout revoga refresh token e limpa estado.

## Identidade Visual
- Logo oficial: três formas arredondadas (verde, charcoal, dourado) + texto "CactusGuard" (Cactus charcoal / Guard dourado).
- Ver spec 035-identidade-visual.md.
- Usar no header, login, favicon e loading.
- Cada módulo do backend possui seu serviço correspondente em `api/services`.
- Todas as operações (listar, detalhar, criar, atualizar, decidir, exportar) chamam endpoints reais.
- O front nunca calcula ou decide o que o backend deve decidir.
