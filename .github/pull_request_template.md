## O que muda e por quê

<!-- Resumo curto + spec relacionada (ex.: specs/012). Link da issue, se houver. -->

## Como foi testado

<!-- Comandos rodados e resultado (mvn verify, npm test, flutter test...). -->

## Checklist regulatório (marque ou escreva "N/A" com o motivo)

- [ ] **Decisão humana**: case só termina em `COMMUNICATE` ou `ARCHIVE` por pessoa; escalar não é decisão; nada automatiza a decisão (exceto espécie ≥ R$ 50 mil → comunicação automática).
- [ ] **Justificativa obrigatória** em toda ação regulatória (decisão, aprovação, reabertura).
- [ ] **Quatro olhos** (PEP / alto risco / EDD): aprovador ≠ quem decidiu, garantido no backend.
- [ ] **Prazos** preservados: seleção ≤ 45 dias da ocorrência; análise ≤ 45 dias da seleção; COAF até D+1 útil da decisão.
- [ ] **CSNU**: hit confirmado continua gerando bloqueio imediato.
- [ ] **PII**: criptografada em repouso, busca só por hash, acesso registrado (`DataAccessService`), nada de PII em log/URL/resposta sem máscara.
- [ ] **Auditoria**: ação relevante registrada no `AuditLog` (append-only); nada apaga/edita trilha.
- [ ] **Migrations**: nenhuma migration existente foi editada; mudança de schema = nova `V<n>__*.sql`.
- [ ] **Tipping-off**: case/alerta/comunicação não aparecem em nenhum canal voltado ao cliente (API, push, e-mail, app).
- [ ] **Segredos**: nenhum segredo novo no código; credencial de dev nova só com entrada explícita no `.gitleaks.toml`.
- [ ] **Multi-tenant**: consultas e escrita respeitam o isolamento por tenant.

## Dúvida regulatória

<!-- Se houve dúvida, qual interpretação conservadora foi adotada e onde foi registrada na spec. -->
