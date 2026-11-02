# 001 — Visão Geral do Projeto

## Objetivo

Desenvolver uma plataforma SaaS (com opção de private cloud / on-prem) de **Prevenção à Lavagem de Dinheiro e Financiamento do Terrorismo (PLD/FT)** para o mercado brasileiro.

A solução deve ser aderente às principais normas brasileiras:

- Circular BCB nº 3.978/2020
- Carta Circular BCB nº 4.001/2020
- Lei nº 9.613/1998
- Requisitos de comunicação ao COAF via SISCOAF

**Público-alvo**: Bancos, Instituições de Pagamento (IPs), Fintechs, SCDs, SEPs e outras instituições autorizadas pelo Banco Central.

**Posicionamento**: Alternativa moderna ao eGuardian (Advice), com foco em API-first, configurabilidade, usabilidade e time-to-value rápido para fintechs e IPs, mantendo a robustez necessária para bancos.

## Princípios de Design

- Abordagem Baseada em Risco (ABR) como princípio central.
- Multi-tenant com isolamento forte de dados.
- Auditabilidade total (trilha imutável).
- Configurabilidade de regras e cenários pelo cliente.
- Separação clara entre dados declarados e observados.
- Versionamento de dados cadastrais e de risco.
- Decisão final sempre humana (o sistema sugere, não decide comunicar ao COAF).
- LGPD by design.
- API-first em todas as camadas.

## Referências

- `CLAUDE.md` (resumo de alto nível)
- Diagramas: `PLD_Arquitetura_Negocio.drawio` e `PLD_Modelo_Dados.drawio`
- Modelo detalhado: `PLD_Modelo_Dados_Detalhado.md`
