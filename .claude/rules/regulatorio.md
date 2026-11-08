---
paths:
  - "cactusguard-backend/src/main/java/br/com/cactus/guard/{alert,case_,rule,siscoaf,kyc,risk,screening,watchlist,transaction,audit,party,report,job}/**"
  - "specs/00[389]-*.md"
  - "specs/01[0-8]-*.md"
---

# Invariantes regulatórias PLD/FT (Circular BCB 3.978/2020, Carta Circular 4.001/2020, Lei 9.613/98, LGPD)

- Decisão final de comunicar ao COAF é sempre humana (SUPERVISOR/ADMIN). Exceção: espécie ≥ R$ 50.000 → comunicação automática.
- Justificativa obrigatória em toda decisão de case e alteração de regra/risco.
- Prazos: 45 dias seleção + 45 dias análise + comunicação até o dia útil seguinte à decisão.
- Proibido tipping-off: nada de alerta/case/comunicação visível ao cliente.
- `audit_log` append-only; retenção mínima de 10 anos (jobs de expurgo respeitam).
- PII (CPF/CNPJ, e-mail, telefone) cifrada AES-256-GCM, busca por hash, mascarada em respostas e nunca em logs.
- Regra de monitoramento sempre versionada; alerta guarda regra + versão + parâmetros + valores observados.
- Case encerrado não reabre. Mudança relevante → `AuditService.log`.
- Dúvida ou mudança nessas áreas: `/revisao-regulatoria` (pld-compliance-reviewer + security-reviewer).
