# CactusGuard Web — Status

## Decisão de Design (2026-11-01)

Aprovado pelo usuário:
- Menu hambúrguer (colapsável) + sidebar expandida no desktop
- Logo: boomerangs arredondados se encontrando (sem círculo central)
- Cores: teal #00c9a7, sidebar #0d3d36
- Tema light com sidebar escura
- Gráficos: linha de alertas + donut de severidade
- Responsivo: web, tablet, mobile

## Specs criadas

| Spec | Arquivo |
|------|---------|
| 043 | specs-frontend/043-design-ui-ux.md |
| 044 | specs-frontend/044-layout-navegacao.md |
| 045 | specs-frontend/045-dashboard.md |

## Estrutura implementada

```
cactusguard-web/
├── src/
│   ├── App.tsx
│   ├── main.tsx
│   ├── components/Sidebar.tsx
│   ├── layouts/AppLayout.tsx
│   ├── pages/Dashboard.tsx
│   └── styles/index.css
├── index.html
├── vite.config.ts
├── tailwind.config.js
├── postcss.config.js
└── package.json
```

## Stack

- React 18 + Vite
- Tailwind CSS
- React Router
- Recharts
- Zustand (pronto para uso)
- Axios (pronto para uso)

## Próximos passos

1. Integração com APIs do backend
2. Páginas: Alertas, Casos, Screening, KYC, Relatórios
3. Autenticação (login + JWT)
4. Store Zustand para estado global
