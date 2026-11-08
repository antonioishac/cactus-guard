# Web — CactusGuard

React 18 + TypeScript + Vite + Tailwind + React Router + Zustand + Axios + Recharts.

- Comandos: `npm run dev` | `npm run build` (roda `tsc`; use para checar tipos).
- Estado atual e próximos passos: `STATUS.md`.
- Design, layout e dashboard: `../specs-frontend/043`, `044`, `045`.
- Telas por módulo: `../specs/021`–`031` (023 alertas, 025 cases, 026 KYC/PEP, 030 SISCOAF…); identidade visual: `../specs/035`. Endpoints: `../specs/033`.
- Tipping-off: case, alerta e comunicação SISCOAF só aparecem para perfis de PLD/compliance/auditoria; nada disso em telas/textos voltados ao cliente.
- PII sempre mascarada na UI, salvo ação explícita de revelar (gera log de acesso no backend).
