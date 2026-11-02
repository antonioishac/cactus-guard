# Spec 026 — Frontend Web: KYC, PEP e Risco

## Objetivo
Visualização completa e interação controlada com dados de KYC, PEPs e classificação de risco.

## Funcionalidades
- Busca e listagem de partes (com filtros por documento, nome, risco, status PEP, source_type).
- Ficha detalhada da parte:
  - Dados cadastrais atuais e histórico de versões
  - UBO e relacionamentos
  - Status PEP (titular, familiar, colaborador) com origem e datas
  - Histórico de classificação de risco
  - Resultados de screening
- Ações de EDD quando o backend indicar necessidade e o usuário tiver permissão.
- Visualização de dados migrados vs onboarded.

## Integração
- Consome Party, KYC, PEP, RiskClassification e Screening.
- Não calcula risco, status PEP nem regras de EDD no front.

## Regras de tela e UX
- Destaque visual forte para PEPs ativos e alto risco.
- Campos somente leitura quando não houver permissão de edição.
- Loading skeleton e empty states.
- Navegação clara entre versões históricas.
