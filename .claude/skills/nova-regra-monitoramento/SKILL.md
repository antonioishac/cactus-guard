---
name: nova-regra-monitoramento
description: Cria ou altera uma regra do motor de monitoramento transacional (rule/engine) mapeada a um cenário da Carta Circular BCB 4.001/2020, com parâmetros configuráveis, versionamento, seed por migration e testes de limiar. Use para qualquer nova tipologia/red flag ou ajuste de parâmetro de regra.
argument-hint: "<CODIGO_REGRA> <cenário/inciso Carta 4.001> [parâmetros]"
---

Regra: $ARGUMENTS

1. Fundamentação: localize o cenário em `specs/009-cenarios-monitoramento.md` (grupo/inciso da Carta 4.001) e o ciclo de vida em `specs/013-ciclo-regras-falsos-positivos.md`. Se útil, consulte o subagente `fraud-analyst-expert` sobre dados necessários e parâmetros iniciais.
2. Defina (registre no PR):
   - [ ] código único (`MonitoringRule.code`), nome, descrição, inciso da Carta 4.001
   - [ ] parâmetros (limiar em R$, janela em dias/horas, quantidade) — configuráveis, nunca hard-coded
   - [ ] severidade e score de risco
   - [ ] dados de entrada exigidos (existem em `transaction`/`party`? senão, migration)
3. Implementação: estratégia nova no motor (`rule/engine`, padrão Strategy) seguindo os 7 tipos existentes; o alerta gerado guarda regra, VERSÃO, parâmetros e valores observados (explicabilidade para o analista).
4. Versionamento: alteração de regra existente cria nova versão (nunca sobrescreve parâmetros usados por alertas antigos) e exige justificativa + `AuditService.log`.
5. Seed: nova migration (`/nova-migration`) com `ON CONFLICT DO NOTHING`.
6. Testes (`/rodar-testes`):
   - [ ] abaixo do limiar não alerta; no limiar exato alerta
   - [ ] fora da janela não alerta
   - [ ] regra inativa não dispara
   - [ ] alerta referencia a versão vigente
   - [ ] espécie ≥ R$ 50.000 continua gerando comunicação automática (não regredir)
7. Finalize com `/revisao-regulatoria`.
