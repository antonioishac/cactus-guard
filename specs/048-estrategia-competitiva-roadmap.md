# 048 — Estratégia Competitiva e Roadmap (PLD + FT + Fraude)

> Spec **norteadora**. Toda fase/agente deve respeitar a ordem e os critérios de saída abaixo.
> Decidido em 2026-11-08: o CactusGuard **não é MVP** — é produto comercial de ponta.

## 1. Posicionamento

**Plataforma FRAML** (Fraud + AML) para o mercado brasileiro: PLD, Financiamento ao Terrorismo e Prevenção a Fraude num único motor, com decisão em **tempo real** e monitoramento contínuo, multi-tenant (SaaS) e multi-segmento.

Concorrentes de referência: eGuardian (Advice), NICE Actimize, SAS AML/Fraud, Oracle FCCM, Feedzai, Hawk AI, ComplyAdvantage, Unit21, Sardine. Diferencial pretendido: **profundidade regulatória brasileira** (BCB, COAF, CVM, SUSEP, PIX/DICT/MED, listas nacionais) + **tecnologia de ponta** (tempo real, grafo, ML explicável, IA assistiva) + **time-to-value** de SaaS moderno.

## 2. Diagnóstico (2026-11-08)

Base correta e auditável (prazos 45+45+D+1, quatro olhos, COE, CSNU, PEP 5 anos, AuditLog append-only, LGPD), mas hoje equivale a um PLD "de conformidade" básico. Lacunas que **bloqueiam venda**:

| # | Lacuna | Situação atual |
|---|---|---|
| B1 | Multi-tenancy | Inexistente (nenhum `tenant_id`) |
| B2 | Tempo real | Motor batch a cada 6 h (`RuleScheduler`) — inútil contra fraude PIX |
| B3 | Regras configuráveis pelo cliente | `switch` em Java; sem builder, backtesting, shadow mode |
| B4 | Engenharia de fornecedor | Sem CI/CD, SAST/DAST, SBOM, pentest; sem plano de DR |
| B5 | SISCOAF real | Provider especulativo; leiaute oficial não validado |

## 3. Matriz de capacidades-alvo

### 3.1 Fraude (tempo real)
- **Decision API síncrona** (p99 < 100 ms): `APPROVE | REVIEW | HOLD | BLOCK` + score + reason codes, para PIX/TED/boleto/cartão, login, cadastro, alteração de dados sensíveis, inclusão de chave PIX, aumento de limite.
- Cenários: conta laranja/mula, account takeover, identidade sintética, golpe com engenharia social (vítima transferindo), falso boleto, primeira transação para novo favorecido, mudança de dispositivo/geo, velocity, horários atípicos, MED/devoluções repetidas.
- Sinais: device fingerprint, IP/geo/VPN, biometria comportamental (via provedores), histórico de favorecidos, marcações do **DICT** (BCB), listas internas de bloqueio.
- Feedback loop: confirmação de fraude/chargeback/MED realimenta regras e modelos.

### 3.2 PLD
- **Todos os 17 grupos** da Carta Circular 4.001/2020 parametrizados por tenant e categoria de risco.
- Perfil x capacidade financeira, peer group, contas de passagem, fracionamento, valores redondos, jurisdições GAFI, espécie e provisionamento ≥ R$ 50 mil (COE automática).
- Reconstrução histórica (estado do cliente e regras vigentes em qualquer data).

### 3.3 Financiamento ao Terrorismo e sanções
- CSNU com **bloqueio imediato** (Lei 13.810/2019, Res. BCB 44/2020) e comunicações correlatas; listas OFAC/ONU/UE/UK com atualização automática; screening de contraparte em tempo real.

### 3.4 KYC/KYB/KYE/KYP
- Enriquecimento automático: Receita (Serpro), **QSA do CNPJ → UBO automático**, PEP oficial (CGU/Portal da Transparência), **CEIS/CNEP**, lista de trabalho escravo, mídia adversa em PT-BR (NLP).
- Screening com **matching fuzzy calibrado** (Jaro-Winkler + fonética PT-BR + transliteração), desempate por nome da mãe/data de nascimento, gestão de homônimos.
- **Entity resolution**: unificar a mesma pessoa/empresa entre fontes.

### 3.5 Inteligência
- **Grafo de vínculos**: atributos compartilhados (dispositivo, telefone, e-mail, endereço, IP, conta destino), fluxos circulares, redes de mulas, comunidades.
- **ML explicável**: priorização de alertas (meta: −50% de falso positivo sem perda de detecção), detecção de anomalias, score de fraude; reason codes legíveis; **governança de modelos** (versão, validação, drift, aprovação, trilha).

### 3.6 Operação do analista
- Workflow configurável, filas inteligentes, ações em lote, dossiê com linha do tempo + grafo, **narrativa de comunicação COAF assistida por IA com revisão humana obrigatória**, SISCOAF no leiaute oficial, AIR e relatório de efetividade gerados dos dados.

### 3.7 Plataforma e comercial
- Multi-tenant com RLS, configuração por tenant, white-label, portal de API + sandbox + SDKs + webhooks, metering/billing.
- Segmentos: BCB (bancos, IPs, SCD/SEP), **CVM (Res. 50/2021)**, **SUSEP**, prestadores de ativos virtuais (regulação BCB — *validar com jurídico*), demais setores obrigados pelo COAF.
- Requisitos de fornecedor: Res. CMN 4.893/2021 (cibersegurança e nuvem), dados no Brasil, ISO 27001 / SOC 2, pentest anual, DR com RPO/RTO definidos, SLA contratual.

## 4. Princípios de arquitetura (decorrem do posicionamento)

1. **Três caminhos de processamento sobre o mesmo modelo de regras**:
   - *Síncrono* (Decision API) — fraude e bloqueios, latência baixa, features pré-computadas;
   - *Streaming* (eventos Kafka) — monitoramento contínuo PLD/fraude em segundos;
   - *Batch* — agregações longas (30/90/180 dias), peer group, backtesting.
2. **Feature store** (Redis para janelas curtas/velocity; Postgres para agregados longos).
3. **Regras como dados**: DSL/JSON versionada, editável por builder no-code, com simulação, backtesting e *shadow mode* antes de ativar (quatro olhos).
4. **ML desacoplado**: treino offline (Python), serving no motor (ONNX Runtime na JVM) para não adicionar salto de rede; sempre com reason codes.
5. **Eventos com outbox transacional** (nada se perde), DLQ e reprocessamento (spec 047).
6. **Multi-tenancy**: `tenant_id` em todas as tabelas + **RLS no PostgreSQL** + filtro Hibernate; testes que provam isolamento.
7. **Integridade**: cadeia de hash no AuditLog (calculada) + ancoragem periódica externa.
8. **Particionamento** por data em transações, eventos e alertas desde a fase 1.
9. Observabilidade: OpenTelemetry (traces/métricas/logs correlacionados), SLOs por caminho.

## 5. Roadmap por fases (ordem obrigatória)

| Fase | Conteúdo | Critério de saída |
|---|---|---|
| **0 — Estabilização** (em andamento) | Compilação, migrations, Java 25/SB4, regras regulatórias, segurança, web, alinhamento de contratos, fundação mobile, push | `mvn verify` + web + mobile verdes; fluxo e2e manual com usuários dev |
| **1 — Fundação de plataforma** | Multi-tenancy (RLS), CI/CD (build, testes, SAST, dependências, SBOM, imagem), OpenTelemetry, Kafka + outbox, hash chain de auditoria, particionamento, config por tenant | Teste automatizado de isolamento entre tenants; pipeline bloqueando PR vermelho; evento nunca perdido (teste de falha) |
| **2 — Motor de decisão unificado** | Decision API síncrona, feature store, DSL de regras + builder, simulação/backtesting/shadow, streaming PLD, listas de bloqueio, cenários de fraude PIX, todos os grupos da Carta 4.001 | p99 < 100 ms em teste de carga; regra criada pelo cliente sem deploy; backtesting com métricas de FP |
| **3 — Inteligência** | Screening fuzzy + listas automáticas BR/internacionais, entity resolution, grafo de vínculos e mulas, ML explicável + governança | Benchmark de matching (precisão/recall) documentado; redução de FP medida em base de teste |
| **4 — Operação do analista** | Workflow configurável, filas, dossiê com grafo/timeline, IA para narrativa, SISCOAF oficial, AIR/efetividade, reconstrução histórica, telas mobile completas | Comunicação gerada validada no leiaute SISCOAF; relatório de efetividade emitido dos dados |
| **5 — Prontidão comercial** | Multi-segmento (CVM/SUSEP/VASP), portal de API/sandbox/SDK/webhooks, onboarding de tenant, billing, teste de carga (spec 041), pentest, DR, ISO 27001/SOC 2 | Due diligence de fornecedor de banco respondível item a item |

Telas (web/mobile) **acompanham** cada fase sobre a base pronta — não antecipam funcionalidades sem backend real.

## 6. Pendências a validar com jurídico/compliance
- Regulação vigente de prestadores de ativos virtuais (BCB) e obrigações de PLD correspondentes.
- Canal/formato das comunicações de indisponibilidade CSNU (BCB/MJSP/COAF).
- Leiaute e forma de envio oficiais do SISCOAF por segmento.
- Uso de IA na narrativa de comunicação (revisão humana obrigatória e trilha).
- Bases de dados de terceiros (contratos, LGPD, base legal de tratamento).
