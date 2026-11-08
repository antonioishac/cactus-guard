# 046 — Catálogo de Campos para Análise PLD/FT e Fraude

## Objetivo
Referência de negócio dos campos que o analista de PLD/fraude precisa ver e analisar, por entidade. É a fonte para modelo de dados (002), telas (021–032) e regras (008/009). Nomes de coluna são sugestões em `snake_case`; a migration é a fonte do schema físico.

Legenda da coluna **Reg.**: `S` = exigido por norma (norma citada); `N` = boa prática/antifraude; `S*` = exigência regulatória genérica cujo campo específico é interpretação nossa **[validar jurídico]**.

Normas: **C3978** = Circular BCB 3.978/2020 · **CC4001** = Carta Circular BCB 4.001/2020 · **L9613** = Lei 9.613/1998 · **L13810** = Lei 13.810/2019 · **R44** = Resolução BCB 44/2020 · **LGPD** = Lei 13.709/2018.

Todo dado pessoal (documento, nome da mãe, e-mail, telefone, conta) é criptografado em repouso, com `*_hash` para busca (040) e acesso registrado em DataAccessLog.

---

## 1. Party — Pessoa Física

| Campo | Por que importa para PLD/fraude | Reg. |
|---|---|---|
| `document` (CPF) + `document_hash` | Identificação única; busca por hash sem expor PII | S — C3978 |
| `name`, `social_name` | Identificação; screening de listas | S — C3978 |
| `mother_name` | Desambigua homônimos em screening (CSNU/PEP/sanções) e detecta identidade sintética | S* — C3978 (qualificação) |
| `birth_date`, `nationality`, `birth_country` | Desambiguação de homônimos; risco de jurisdição | S* — C3978 |
| `rf_status` (situação cadastral Receita: regular, suspensa, cancelada, **titular falecido**, pendente) + `rf_checked_at` | CPF irregular ou de falecido em movimentação = forte indício de fraude/conta de passagem | N (antifraude; CC4001 grupo III) |
| `occupation` / CBO | Coerência entre atividade e movimentação | S — C3978 |
| `declared_income`, `declared_net_worth`, `income_reference_date` | Capacidade financeira: base da regra "movimentação incompatível" | S — C3978; CC4001 |
| `source_of_funds`, `source_of_wealth` | Origem dos recursos/patrimônio; obrigatório em EDD | S — C3978 (EDD/PEP) |
| `relationship_purpose` | Propósito e natureza do relacionamento; baseline do perfil esperado | S — C3978 |
| `address`, `country_of_residence` | Jurisdição de risco; município de risco (CC4001) | S — C3978 |
| `email`, `phone` | Contato; reuso entre clientes distintos indica rede/fraude | N |
| `is_pep`, `pep_status` | Ver seção 4 | S — C3978 |
| `risk_score`, `risk_category`, `risk_methodology_version` | ABR; intensidade de monitoramento | S — C3978 |
| `relationship_start`, `last_kyc_review_at`, `next_kyc_review_at` | Atualização cadastral proporcional ao risco | S — C3978 |
| `source_type` (migrated/onboarded) | Rastreabilidade da origem do dado | N |
| `blocked_csnu`, `blocked_at` | Indisponibilidade de ativos por sanção CSNU | S — L13810; R44 |

## 2. Party — Pessoa Jurídica

| Campo | Por que importa para PLD/fraude | Reg. |
|---|---|---|
| `document` (CNPJ) + `document_hash` | Identificação | S — C3978 |
| `legal_name`, `trade_name` | Identificação; screening | S — C3978 |
| `incorporation_date` (data de abertura) | Empresa recém-aberta com alto volume = indício de empresa de fachada | N (CC4001 grupo III/IV) |
| `rf_status` (ativa, inapta, baixada, suspensa) + `rf_checked_at` | PJ inapta/baixada movimentando é indício forte | N |
| `cnae_primary`, `cnae_secondary[]` | Atividade de alto risco; coerência atividade × transações | S — C3978 |
| `legal_nature` (natureza jurídica) | Estruturas opacas (fundos, associações, offshores) elevam risco | S* — C3978 |
| `declared_revenue`, `declared_equity`, `revenue_reference_date` | Capacidade financeira da PJ | S — C3978 |
| `share_capital` | Capital social desproporcional ao volume movimentado | N |
| `source_of_funds`, `relationship_purpose` | Idem PF | S — C3978 |
| `legal_representatives[]` / procuradores | Screening de representantes; fraude por procuração | S — C3978 |
| `ubo_identified` (bool) + `ubo_not_identified_reason` | UBO não identificado ⇒ risco alto + EDD | S — C3978 |
| `headquarters_country`, `operating_countries[]` | Jurisdições de risco (GAFI) | S — C3978 |

## 3. Beneficiário Final (UBO)

| Campo | Por que importa | Reg. |
|---|---|---|
| `company_party_id`, `owner_party_id` | Liga PJ à pessoa natural | S — C3978 |
| `ownership_pct_direct`, `ownership_pct_indirect`, `ownership_pct_total` | Limite ≥ 25% (instituição pode adotar menor), direta **ou indireta** | S — C3978 |
| `control_type` (participação, controle de fato, administrador) | Beneficiário por controle, não só por participação | S* — C3978 |
| `ownership_chain` (cadeia societária: lista de PJs intermediárias + % por elo) | Navegar camadas; detectar estruturas opacas e offshores | S — C3978 |
| `chain_depth`, `has_offshore_layer` | Fatores de risco de complexidade | N |
| `is_pep` do UBO | PEP oculto atrás de PJ | S — C3978 |
| `valid_from`, `valid_to`, `calculated_at`, `source` | Reconstrução histórica | S — C3978 (reconstrução/auditoria) |

## 4. PEP (titular e relacionados)

| Campo | Por que importa | Reg. |
|---|---|---|
| `pep_role` (cargo/função), `pep_organ`, `pep_jurisdiction` (BR/exterior) | Enquadramento no rol de cargos | S — C3978 |
| `role_start_date`, `role_end_date` | Base do cálculo de expiração | S — C3978 |
| `pep_expiry_date` = `role_end_date` + 5 anos | Status PEP vale até 5 anos após deixar o cargo | S — C3978 |
| `pep_status` (active, expired, related) | Controle do ciclo de vida, sem apagar histórico | S — C3978 |
| `pep_relationship_type` (familiar até 2º grau, cônjuge/companheiro, enteado, estreito colaborador, representante) + `pep_holder_party_id` | Relacionados também exigem diligência | S — C3978 |
| `senior_approval_by`, `senior_approval_at`, `senior_approval_justification` | Aprovação da alta administração para iniciar/manter relacionamento | S — C3978 **[validar jurídico: artigo]** |
| `pep_source` (lista, provedor, autodeclaração) + `screened_at` | Evidência da origem da informação | N |

## 5. Transação

| Campo | Por que importa | Reg. |
|---|---|---|
| `occurred_at` (data de ocorrência) | Início do prazo de 45 dias para seleção | S — C3978 |
| `amount`, `currency` | Limiares; comunicação automática ≥ R$ 50 mil | S — C3978 |
| `direction` (crédito/débito) | Contas-passagem: entrada × saída | S — C3978 |
| `type` (depósito, saque, **provisionamento de saque**, TED, PIX, boleto, cartão, câmbio…) | Provisionamento de saque em espécie também é comunicação automática | S — C3978 |
| `is_cash` (espécie) | Fluxo de comunicação automática; CC4001 grupo I | S — C3978; CC4001 |
| `payment_method` / `channel` (agência, app, internet banking, correspondente, ATM) | Risco de canal (ABR) | S — C3978 |
| `pix_end_to_end_id` (E2E) | Rastreio inequívoco da operação PIX; cruza com MED/devoluções | N |
| `counterparty_document_hash`, `counterparty_name` | Screening da contraparte; rede de relacionamentos | S* — C3978 |
| `counterparty_ispb`, `counterparty_branch`, `counterparty_account` | Identifica instituição/conta destino; detecta contas de passagem e mulas | N |
| `counterparty_country` | Jurisdição de alto risco (GAFI); remessas ao exterior | S — C3978; CC4001 |
| `own_branch`, `own_account` | Múltiplas contas do mesmo cliente com fragmentação | N |
| `purpose_declared` (quando houver, ex.: câmbio) | Coerência econômica | N |
| `external_reference`, `source_batch_id` | Rastreabilidade até o sistema de origem | S — C3978 (auditoria) |

## 6. Alerta

| Campo | Por que importa | Reg. |
|---|---|---|
| `rule_id` + **`rule_version`** | Prova qual parametrização gerou o alerta (reconstrução e auditoria do regulador) | S — C3978 |
| `scenario_code` / `cc4001_indicator` (inciso/grupo da Carta 4.001) | Mapeia alerta ao indício normativo; relatório de efetividade | S* — CC4001 |
| `occurred_at` (fato gerador mais antigo) | Prazo de seleção: 45 dias da ocorrência | S — C3978 |
| `selection_deadline` = `occurred_at` + 45 dias | SLA regulatório de seleção | S — C3978 |
| `severity`, `risk_category_at_trigger` | Priorização (ABR) | S — C3978 |
| `triggering_transaction_ids[]`, `evidence_snapshot` (valores/parâmetros avaliados) | Explicabilidade do alerta | S — C3978 |
| `status`, `assigned_to`, `false_positive_reason` | Gestão de falsos positivos com justificativa | S — C3978 |
| `dedup_key` | Evita alertas duplicados | N |

## 7. Case / Dossiê

| Campo | Por que importa | Reg. |
|---|---|---|
| `selected_at` (data de seleção) | Início do prazo de análise | S — C3978 |
| `analysis_deadline` = `selected_at` + 45 dias | SLA de análise formalizada | S — C3978 |
| `decision` (`COMMUNICATE` \| `ARCHIVE`) | Únicas decisões finais | S — C3978 |
| `justification` (obrigatória) | Fundamentação da decisão | S — C3978 |
| `decided_by`, `decided_at` | Trilha de quem decidiu | S — C3978 |
| `requires_approval` (PEP/alto risco/EDD), `approved_by`, `approved_at`, `approval_justification` | Quatro olhos; `approved_by` ≠ `decided_by` | S* — C3978 (aprovação de nível superior) |
| `communication_deadline` = próximo dia útil após decisão | Prazo D+1 de comunicação ao COAF | S — C3978 |
| `escalations[]` (de, para, motivo, data) | Histórico de escalação (não é decisão) | N |
| `cc4001_indicators[]` | Indícios da Carta 4.001 que fundamentam o caso | S* — CC4001 |
| `evidences[]` (arquivo, `sha256`, `uploaded_by`, `uploaded_at`) | Integridade da prova; cadeia de custódia | S* — C3978 (dossiê) |
| `party_snapshot`, `risk_snapshot`, `screening_snapshot` | Dossiê com estado na data da análise (reconstrução) | S — C3978 |
| `retention_until` = encerramento + 10 anos | Retenção | S — C3978 |

## 8. Comunicação SISCOAF

| Campo | Por que importa | Reg. |
|---|---|---|
| `type` (COS suspeita, COE automática espécie, CSNU, declaração negativa) | Fluxos distintos | S — C3978; L9613; L13810 |
| `case_id` / `transaction_ids[]` | Vínculo com o fato | S — C3978 |
| `deadline`, `sent_at`, `coaf_protocol` | Prova de tempestividade | S — C3978 |
| `payload` (criptografado) + `payload_hash` | Conteúdo enviado e integridade | S — C3978; LGPD |
| `status`, `attempts`, `last_error` | Reenvio com circuit breaker; nada se perde | N |
| `reference_year` (declaração negativa) | Controle anual; prazo **[validar jurídico]** | S — C3978 |
| `visible_to_customer` = sempre falso (regra, não campo editável) | Vedação de dar ciência | S — L9613 art. 11, II |

## 9. Regra de Monitoramento

| Campo | Por que importa | Reg. |
|---|---|---|
| `code`, `version`, `scenario_type`, `cc4001_indicator` | Rastreabilidade normativa | S — C3978; CC4001 |
| `parameters` (limiares, janelas, multiplicadores) | Documentação dos parâmetros | S — C3978 |
| `rationale` (justificativa da regra/limiar) | Regra precisa ser justificável | S — C3978 |
| `created_by`, `approved_by`, `approved_at` (quatro olhos para ativar) | Governança de alteração | S* — C3978 |
| `active_from`, `active_to` | Reconstruir regras vigentes em qualquer data | S — C3978 |
| `backtest_result` (volume de alertas, taxa de falso positivo) | Calibração e relatório de efetividade | N (013) |
| `applies_to_risk_categories[]`, `applies_to_pep` | Limiares diferenciados (PEP/alto risco) | S — C3978 (ABR) |
