---
paths:
  - "cactusguard-web/**/*.{ts,tsx,css,html}"
  - "cactusguard-web/*.{json,js,ts}"
---

# Frontend (React + Vite + TypeScript + Tailwind)

- Specs: 021–031 (telas), 033 (contratos API), 035 (identidade visual), `specs-frontend/043–045` (design, layout, dashboard).
- TypeScript estrito, sem `any`; tipos espelham os DTOs do backend.
- Listas paginadas no servidor; PII só como vem mascarada da API; nunca em `console.log`, URL ou localStorage.
- Ações regulatórias (comunicar COAF, encerrar case, alterar regra) pedem justificativa obrigatória + confirmação; UI por role é conveniência, a barreira é o backend.
- Verificação: `cd cactusguard-web && npm run build`.
