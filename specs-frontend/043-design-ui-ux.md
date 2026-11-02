# Spec 043 — Decisão de Design UI/UX Web

## Data
2026-11-01

## Decisão

Aprovado o design do frontend web do CactusGuard.

## Layout

| Elemento | Decisão |
|----------|---------|
| **Menu** | Hambúrguer (colapsável) + versão expandida no desktop |
| **Logo** | Boomerangs arredondados se encontrando (sem círculo central) |
| **Cores** | Teal #00c9a7 como accent, sidebar dark teal #0d3d36 |
| **Tema** | Light (fundo branco), sidebar escura |
| **Gráficos** | Linha de alertas + donut de severidade |
| **Responsivo** | Web, tablet e mobile |

## Stack Frontend

| Item | Tecnologia |
|------|------------|
| Framework | React 18 + Vite |
| Estado | Zustand |
| HTTP | Axios |
| Roteamento | React Router |
| UI | Tailwind CSS |
| Gráficos | Recharts |

## Estrutura

```
cactusguard-web/
├── src/
│   ├── components/     # Componentes reutilizáveis
│   ├── pages/          # Páginas (Dashboard, Alertas, etc)
│   ├── layouts/        # Layout principal com menu
│   ├── services/       # Chamadas API
│   ├── stores/         # Zustand stores
│   └── styles/         # CSS global
```

## Próximos passos

1. Setup do projeto Vite + React + Tailwind
2. Layout com menu hambúrguer/expandido
3. Página Dashboard com cards e gráficos
4. Integração com APIs do backend
