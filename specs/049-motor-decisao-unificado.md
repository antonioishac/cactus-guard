# 049 — Motor de Decisão Unificado (Fase 2)

> Status: **proposta para aprovação do dono do produto** (2026-11-09). Nenhum código antes da aprovação das decisões em aberto (§14).
> Depende de: 048 (norteadora), 047 (padrões, multi-tenancy, eventos), 008/009/013 (regras e cenários), 003 (prazos, COE, CSNU), 040 (criptografia), 038 (observabilidade), 041 (carga).
> Critérios de saída da Fase 2 (048 §5): **p99 < 100 ms em teste de carga reproduzível**; **regra criada pelo cliente sem deploy**; **backtesting com métricas de falso positivo**.

## 1. Objetivo, escopo e não-escopo

**Objetivo**: um único motor que avalia **as mesmas regras (dados, não código)** em três caminhos, para fraude, PLD e FT:

| Caminho | Gatilho | Latência | Uso típico | Fonte de features |
|---|---|---|---|---|
| **SYNC** (Decision API) | Banco chama `POST /api/v1/decisions` antes de efetivar a operação | p99 < 100 ms | Fraude PIX/ATO, listas de bloqueio, CSNU, step-up | Redis (pré-computado) + memória local |
| **STREAM** | Evento Kafka (`cactus.transactions.v1`, `cactus.decision-events.v1`) | segundos | PLD contínuo (fracionamento, passagem, espécie, PEP), fraude pós-fato, atualização de features | Redis + perfil |
| **BATCH** | Agendado (substitui gradualmente o `RuleScheduler` de 6 h) | minutos/horas | Janelas 30/90/180 d, peer group, perfil × capacidade, backtesting | PostgreSQL (consultas por janela com poda de partição) |

**Escopo Fase 2**: Decision API; feature store; DSL de regras + validação + avaliador explicável; políticas por canal/produto; listas de bloqueio/permissão; simulação, backtesting, shadow/champion-challenger; streaming PLD sobre a DSL; migração dos 13 cenários Java (`RuleEngineService`) para DSL; cenários de fraude PIX; cobertura dos grupos da Carta 4.001 (§9); feedback (fraude confirmada/MED/chargeback/falso positivo); builder no-code web (consome a API de regras).

**Não-escopo Fase 2** (vai para a Fase 3/4): modelos de ML (só a **porta** e o lugar no score, §8); grafo de vínculos; screening fuzzy novo; device fingerprint/biometria **próprios** (só portas para provedores, §9.3); gRPC (opcional, §3.8); SDKs públicos (Fase 5).

**Ponto de partida** (código atual): `rule/engine/RuleEngineService` faz `switch` por código (13 cenários) com SQL por janela; `MonitoringRule` já tem `parameters` JSONB, `version`, `status`, `approvedBy`, `effectiveFrom/To`, `autoCommunication`, `carta4001Reference`; `MonitoringRuleVersion` existe; eventos via outbox (`DomainEventPublisher` → `OutboxRelay`), consumidor `TransactionMonitoringConsumer`. Spec 041 mediu **150–400 ms** por consulta de janela em 2 mi linhas ⇒ **SQL por janela está proibido no caminho SYNC**.

## 2. Modelo de domínio

| Conceito | Definição | Persistência |
|---|---|---|
| **DecisionEvent** | Fato a decidir/monitorar: `eventType`, canal, parte, operação normalizada, sinais (dispositivo/IP/geo/DICT) | Entrada (não persistida integralmente; ver §10) |
| **Decision** | Resposta: `outcome`, `score`, reason codes, ações, versões, trilha, latência, `degraded` | `decisions` (particionada) |
| **Rule** | Definição DSL versionada (§6). Reaproveita `monitoring_rules` (+ coluna `definition`) | `monitoring_rules` / `monitoring_rule_versions` |
| **Policy (RuleSet)** | Conjunto **imutável e versionado** de versões de regras + thresholds de score + config de degradação, por `(tenant, eventType[], canal/produto)` | `decision_policies`, `decision_policy_versions` |
| **Feature** | Valor derivado e nomeado do catálogo (§5), com tipo, janela, chave e caminhos onde existe | Catálogo versionado em arquivo; valores em Redis/PG |
| **List** | Lista nomeada por tenant (`BLOCK`/`ALLOW`/`WATCH`) de valores **hash** (documento, chave PIX, conta, dispositivo, IP) ou códigos (país, MCC) com vigência | `decision_lists`, `decision_list_entries` |
| **Score** | Inteiro 0–1000 (§8) | em `decisions` |
| **ReasonCode** | Código estável + texto para analista + categoria (`FRAUD`/`AML`/`CFT`) + `confidential` (PLD/FT ⇒ nunca repassar ao cliente final) | Catálogo por tenant (códigos de sistema imutáveis) |
| **Action** | Ação recomendada ao sistema do banco ou interna (tabela §3.4) | em `decisions` |
| **Feedback** | Rótulo posterior: `FRAUD_CONFIRMED`, `MED_RECEIVED`, `CHARGEBACK`, `FALSE_POSITIVE`, `LEGIT_CONFIRMED`, `CASE_COMMUNICATED`, `CASE_ARCHIVED` | `decision_feedback` |

**Tipos de evento** (`EventType`):

| Grupo | Valores | Campos-chave (além de parte/canal/hora) |
|---|---|---|
| Transação | `PIX_OUT`, `PIX_IN`, `TED_OUT`, `TED_IN`, `BOLETO_PAYMENT`, `CARD_AUTH`, `CASH_DEPOSIT`, `CASH_WITHDRAWAL`, `CASH_WITHDRAWAL_PROVISION`, `FX_OPERATION` | valor BRL, contraparte (hash doc, ISPB, conta hash, chave PIX hash, país), `endToEndId`, iniciação PIX (`MANUAL`/`QR`/`AGENDADO`/`AUTOMATICO`) |
| Sessão | `LOGIN` | resultado, fator, dispositivo, IP |
| Cadastro | `ONBOARDING` | tipo de pessoa, idade declarada, renda declarada (faixa), dispositivo |
| Alterações sensíveis | `SENSITIVE_DATA_CHANGE` (telefone, e-mail, senha, endereço, dispositivo autorizado), `PIX_KEY_REGISTRATION`, `PIX_KEY_PORTABILITY`, `LIMIT_INCREASE`, `PAYEE_ADD` | campo alterado (enum), valor anterior/novo **só como hash**, limite anterior/novo |
| Rótulo | `MED_NOTIFICATION`, `CHARGEBACK` (assíncronos, alimentam features e feedback) | `endToEndId`, motivo |

## 3. API de decisão síncrona

### 3.1 Autenticação máquina-a-máquina
- **OAuth2 client credentials por tenant**: `POST /api/v1/oauth/token` (`grant_type=client_credentials`) emite JWT de **5 min** com `tenant_id`, `client_id`, `scope=decision:write decision:feedback`. Segredo armazenado com Argon2id em `service_clients`; rotação com dois segredos válidos simultâneos; auditoria de emissão/rotação.
- Opcional por tenant: **mTLS** com certificado vinculado ao `client_id` (`cnf.x5t#S256` no token). Recomendado para bancos (048 §3.7, Res. CMN 4.893).
- Validação do JWT **local** (chave em memória; zero chamada externa). Tenant **só** do token — nunca do corpo.

### 3.2 Request
`POST /api/v1/decisions` — headers `Authorization`, `Idempotency-Key` (obrigatório, ≤ 64 chars), `traceparent` (opcional).

```json
{
  "eventType": "PIX_OUT",
  "occurredAt": "2026-11-09T14:03:22.120-03:00",
  "channel": "MOBILE",
  "product": "PF_CONTA_PAGAMENTO",
  "party": { "externalId": "c-88123", "accountId": "a-55012" },
  "operation": {
    "amount": "4900.00", "currency": "BRL",
    "pix": { "initiation": "MANUAL", "keyType": "EVP", "key": "<em claro, hasheada na borda>", "endToEndId": "E1234..." },
    "counterparty": { "document": "<em claro, hasheado na borda>", "ispb": "18236120", "account": "<hasheada na borda>", "country": "BR" }
  },
  "device": { "id": "d-9f2c", "fingerprintProvider": "X", "riskScore": 71, "rooted": false, "emulator": false },
  "network": { "ip": "189.1.2.3", "vpn": false },
  "dict": { "keyFraudMarks": 2, "accountFraudMarks": 0, "keyCreatedDaysAgo": 3 },
  "context": { "sessionAgeSec": 45, "onCall": null }
}
```
- **Minimização (LGPD)**: documento, chave PIX, conta e IP só trafegam (TLS) e são convertidos **na borda** em HMAC (`DocumentHash`/040) e, para IP, em país/UF/ASN + hash; o valor em claro **nunca** é logado, persistido, evento ou feature. Nome de contraparte **não é aceito** (rejeitado com 400).
- `party.externalId` é o id do cliente no banco; o motor resolve para `party_id` via índice em memória/Redis (`ext→party`). Parte desconhecida ⇒ features de perfil `Missing(UNKNOWN_PARTY)` (não erro).
- `occurredAt` com desvio > 5 min do relógio do servidor ⇒ usa hora do servidor para janelas e grava ambos.

### 3.3 Response (200)
```json
{
  "decisionId": "0193f0e2-...", "outcome": "HOLD", "score": 812,
  "reasons": [
    { "code": "FR_ATO_NEW_DEVICE_NEW_PAYEE", "category": "FRAUD", "contribution": 420, "confidential": false,
      "text": "Dispositivo novo (12 min) + primeiro envio ao favorecido + valor 6,1x o ticket médio" },
    { "code": "FR_DICT_KEY_MARKED", "category": "FRAUD", "contribution": 260, "confidential": false, "text": "Chave destino com 2 marcações de fraude no DICT" }
  ],
  "actions": ["STEP_UP_AUTH", "DELAY_PIX"],
  "customerMessageCode": "GENERIC_SECURITY_CHECK",
  "policy": { "id": "pol-pix-mobile", "version": 14 },
  "rules": [ { "code": "FR_ATO_01", "version": 3 }, { "code": "FR_DICT_01", "version": 1 } ],
  "degraded": false, "replayed": false, "latencyMs": 23
}
```
- `outcome` (ordem de severidade): **APPROVE** (seguir) < **REVIEW** (seguir + alerta pós-fato) < **HOLD** (reter até step-up/confirmação/analista, com prazo) < **BLOCK** (recusar).
- `customerMessageCode` é o **único** campo destinado ao canal do cliente, de catálogo genérico (`GENERIC_SECURITY_CHECK`, `GENERIC_UNAVAILABLE`). Reason codes `confidential=true` (AML/CFT) **não podem** ser exibidos ao cliente — cláusula contratual + documentação da API (§12).

### 3.4 Ações
| Ação | Efeito esperado no banco | Observação |
|---|---|---|
| `STEP_UP_AUTH` | Exigir fator adicional | Fraude |
| `DELAY_PIX` | Postergar efetivação N min (param.) | Usa regras PIX de horário/limites — *validar com jurídico o enquadramento no Regulamento PIX* |
| `CAUTIONARY_BLOCK_IN` | Bloqueio cautelar de valor **recebido** | Previsto no Regulamento PIX (bloqueio cautelar, até 72 h) — *validar com jurídico prazos/condições vigentes* |
| `REDUCE_LIMIT` | Reduzir limite temporariamente | |
| `CONTACT_CUSTOMER_OOB` | Contato ativo por canal oficial | **Só para categoria FRAUD**; proibido se houver reason AML/CFT (validador bloqueia combinação) |
| `OPEN_ALERT` | Interno: gera alerta (§10.4) | |
| `COE_AUTOMATIC` | Interno: comunicação automática espécie ≥ R$ 50 mil | Nunca bloqueia a operação |
| `FREEZE_ASSETS_CSNU` | Indisponibilidade imediata (Lei 13.810) | `BLOCK` + fluxo CSNU (003/011) |

### 3.5 Idempotência
- `SET NX` em Redis `cg:{t:<tenant>}:idem:<key>` com digest SHA-256 do corpo normalizado e a resposta (TTL 24 h). Mesma chave + mesmo digest ⇒ devolve a resposta original com `replayed=true`. Mesma chave + digest diferente ⇒ **409**.
- Redis indisponível ⇒ segue sem dedup e marca `idempotency_unverified`; a persistência tem `UNIQUE (tenant_id, idempotency_key, decided_at)`-equivalente via tabela de chaves diária (§10.1) e o consumidor descarta duplicata.

### 3.6 Timeout e degradação
- **Deadline interno 80 ms** (configurável ≤ 90). Ao estourar: interrompe o que faltar, avalia só o que já tem e aplica a **política de degradação**. Recomendação contratual ao banco: timeout de cliente **150 ms** e fallback local idêntico à configuração do tenant.
- Independem de Redis/Kafka (memória local, recarregada por evento): **lista CSNU com indisponibilidade ativa** e **listas BLOCK do tenant** (conjuntos de hashes; tamanho típico < 1 mi entradas ⇒ < 64 MB por instância; acima disso, Bloom filter local + confirmação Redis). Hit ⇒ `BLOCK` mesmo degradado.
- Regra com feature ausente segue `onMissing` (§6.2). Se ≥ 1 feature **obrigatória** da política estiver ausente, ou o deadline estourar, `degraded=true` e o resultado é `max(outcome avaliado, outcome de degradação)` quando `FAIL_CLOSED`, ou o outcome avaliado quando `FAIL_OPEN`.

Configuração por `tenant × eventType` (padrões propostos):

| Evento | Padrão | Por quê |
|---|---|---|
| `PIX_OUT`/`TED_OUT` ≤ `degradedAmountLimit` (padrão R$ 1.000) | `FAIL_OPEN` (APPROVE + REVIEW pós-fato no STREAM) | Disponibilidade do PIX é exigida do participante; recusa em massa causa dano ao cliente e risco regulatório (*validar com jurídico*) |
| `PIX_OUT`/`TED_OUT` acima do limite | `FAIL_CLOSED` → `HOLD` (`DELAY_PIX`) | Golpes de alto valor concentram perda; HOLD curto é reversível |
| `LIMIT_INCREASE`, `PIX_KEY_REGISTRATION`, `PIX_KEY_PORTABILITY`, `SENSITIVE_DATA_CHANGE`, `PAYEE_ADD` | `FAIL_CLOSED` → `HOLD` | Precursores clássicos de ATO; podem esperar sem dano relevante |
| `LOGIN` | `FAIL_OPEN` | Bloquear login em massa é indisponibilidade total |
| `ONBOARDING` | `FAIL_CLOSED` → `REVIEW` | Abertura pode aguardar análise |
| `CASH_*`, `PIX_IN`, `TED_IN` | `FAIL_OPEN` | Controle é PLD pós-fato; COE de espécie é garantida pelo STREAM/BATCH, não pelo SYNC |

Riscos: **fail-open** ⇒ perda por fraude durante a falha e possível questionamento de controles (Res. BCB 4.893/Regulamento PIX — *validar*); mitigado por reavaliação STREAM de toda decisão `degraded` (gera alerta se a regra teria disparado). **Fail-closed** ⇒ indisponibilidade percebida, reclamações, impacto em SLA do PIX. Toda alteração dessa configuração exige **quatro olhos** e é auditada; taxa de `degraded` tem SLO e alerta (§11).

### 3.7 Rate limit e códigos HTTP
- Cota por `client_id` (padrão 300 req/s, burst 600; configurável por contrato), reaproveitando o `RateLimitFilter` (039) com contador Redis e **fallback local** se Redis cair (não pode virar 503).
- `200` sempre que houver decisão (inclusive degradada); `400` payload inválido/PII proibida; `401/403` auth/escopo; `409` idempotência; `429` cota; `503` só se nem a decisão degradada for possível (instância sem política carregada).

### 3.8 Outros endpoints
| Endpoint | Uso | Quem |
|---|---|---|
| `POST /api/v1/decision-events` | Eventos assíncronos (202): LOGIN sem decisão, MED, chargeback, alterações já efetivadas | Serviço do banco |
| `POST /api/v1/decisions/{id}/feedback` | Rótulos (fraude confirmada, FP, MED) | Serviço do banco / analista |
| `GET /api/v1/decisions/{id}`, `GET /api/v1/decisions?partyId&from&to` | Consulta com trilha completa | ANALYST+ (nunca canal do cliente); acesso auditado |
| `/api/v1/decision-rules/**`, `/api/v1/decision-policies/**`, `/api/v1/decision-lists/**` | Ciclo de vida (§7) | RULE_EDITOR / SUPERVISOR / ADMIN |
| gRPC `DecisionService.Decide` | Mesmo contrato em protobuf | **Opcional**, só sob demanda de cliente (§14) |

## 4. Orçamento de latência (medido no servidor, da entrada HTTP ao último byte)

| Etapa | p50 alvo | p99 teto | Como |
|---|---|---|---|
| Parse + validação + hash de PII na borda | 0,5 ms | 2 ms | Jackson + HMAC; sem reflexão pesada |
| Auth (JWT local) + tenant | 0,2 ms | 1 ms | Chave em memória |
| Idempotência (Redis `SET NX`) | 0,8 ms | 4 ms | Pode ir no mesmo pipeline do nó da parte |
| Política (cache em memória versionado) | < 0,05 ms | 0,2 ms | `AtomicReference<PolicySnapshot>` por tenant |
| Listas locais (CSNU, BLOCK) | < 0,05 ms | 0,2 ms | `HashSet` de hashes |
| Features (Redis Cluster, pipeline por nó em paralelo) | 2 ms | 15 ms (timeout duro 25 ms) | ≤ 1 round-trip por nó; scripts Lua só para escrita |
| Provider externo síncrono (se a política exigir) | 0 | 30 ms (timeout duro, circuit breaker) | Preferir sinais no request (§9.3) |
| Avaliação de regras | 0,3 ms | 3 ms | AST compilada em closures; ≤ 300 regras/política |
| Score + reason codes | < 0,1 ms | 0,5 ms | |
| Registro durável (Kafka `acks=all`) | 3 ms | 15 ms (timeout 20 ms → fallback PG) | §10.2 |
| Serialização + rede + margem GC/JIT | 2 ms | 15 ms | ZGC generacional; virtual threads |
| **Total** | **~10–15 ms** | **< 100 ms** (soma de tetos ≈ 86 ms com provider; ≈ 56 ms sem) | |

**Proibido no caminho SYNC**: SQL por janela ou qualquer `SELECT` em `transactions`; chamada externa sem timeout ≤ 30 ms e circuit breaker; `KEYS`/`SCAN`/`ZRANGE` sem limite no Redis; regex definida pelo usuário; I/O de log síncrono com payload; carregamento de política sob demanda do banco; avaliação de shadow (vai para virtual thread após a resposta).

## 5. Feature store

### 5.1 Princípios
- Feature = **id estável** `<domínio>.<métrica>.<entidade>.<janela>` (ex.: `pix.out.sum.party.1h`), tipo (`MONEY`, `INT`, `DECIMAL`, `BOOL`, `DURATION_MIN`, `ENUM`, `CODE`), caminhos onde existe (`SYNC/STREAM/BATCH`), custo (unidades Redis) e semântica de ausência. Catálogo em `src/main/resources/decision/feature-catalog-v1.yaml` (fonte única; validador e builder leem dele).
- Entidades-chave (sem PII): `party` (UUID), `cp` (hash da contraparte), `pixkey` (hash), `acct` (hash ISPB+agência+conta), `device` (hash do id), `ip` (hash), `tenant`.
- **Tentativas vs liquidadas**: o SYNC grava **tentativa** (após responder, assíncrono, ~1 ms) — sem isso, 10 PIX em 5 s escapam da velocity porque o evento de transação chega depois. O STREAM grava **liquidadas** (de `cactus.transactions.v1`). Features distintas: `...attempt...` vs `...out...`.

### 5.2 Catálogo inicial (v1)

| # | Feature(s) | Definição precisa | Chave | Janelas | Fonte / atualização | Custo |
|---|---|---|---|---|---|---|
| 1 | `pix.out.count/sum.party.W`, `txn.out.count/sum.party.W`, `txn.in.count/sum.party.W` | Nº e soma (BRL) de operações liquidadas da parte, por direção (PIX ou todas) | party | 1m, 10m, 1h, 24h, 7d | STREAM; backfill PG 7 d | 1 |
| 2 | `pix.attempt.count/sum.party.W` | Tentativas avaliadas no SYNC (qualquer outcome) | party | 1m, 10m, 1h | SYNC pós-resposta | 1 |
| 3 | `txn.in.count/sum.cp.W`, `pix.in.distinct_payers.pixkey.W` | Recebimentos na contraparte/chave destino (dentro do tenant); pagadores distintos (HLL) | cp, pixkey | 1h, 24h, 7d | STREAM | 1–2 |
| 4 | `device.distinct_parties.W`, `ip.distinct_parties.W`, `ip.count.W` | Partes distintas usando o dispositivo/IP (HLL); operações por IP | device, ip | 1h, 24h, 7d | SYNC+STREAM (inclui LOGIN) | 1 |
| 5 | `payee.is_new`, `payee.relationship_age_days`, `payee.txn_count_total` | Primeiro envio da parte para aquela contraparte/chave; dias desde o 1º envio liquidado; total histórico | party×cp | 400 d | STREAM; backfill PG 400 d | 1 |
| 6 | `party.account_age_days`, `party.relationship_age_days` | Dias desde abertura da conta / início do relacionamento | party | — | Perfil (evento `cactus.parties.v1` + noturno) | 0* |
| 7 | `device.is_new_for_party`, `device.first_seen_min`, `party.last_device_change_min` | Dispositivo nunca visto para a parte; minutos desde 1ª vez; minutos desde a última troca de dispositivo autorizado | party | 180 d (máx. 20 dispositivos) | SYNC+STREAM+LOGIN | 1 |
| 8 | `party.last_sensitive_change_min`, `party.last_pix_key_change_min`, `party.last_limit_increase_min` | Minutos desde a última alteração sensível/chave/limite | party | 30 d | `decision-events` | 0* |
| 9 | `geo.uf_unusual`, `geo.country_not_br`, `geo.impossible_travel`, `ip.is_anonymizer` | UF do IP fora das UFs com ≥ 5% das operações em 90 d; país ≠ BR; deslocamento > 900 km/h desde o último evento; VPN/proxy/Tor (sinal/provedor) | party, ip | 90 d | Perfil + SYNC | 1 |
| 10 | `time.hour_share` , `time.hour_atypical` | Fração das operações da parte (90 d) na mesma hora ±1; atípico se < 2% e ≥ 30 operações no histórico | party | 90 d | Perfil (batch noturno + incremento STREAM) | 0* |
| 11 | `amount.to_avg_ticket_90d`, `amount.to_p95_90d`, `amount.zscore_90d` | Valor ÷ ticket médio; valor ÷ p95; z-score (log-valor). Perfil < 10 operações ⇒ `Missing(INSUFFICIENT_HISTORY)` | party | 90 d | Perfil (batch noturno, t-digest serializado) | 0* |
| 12 | `flow.out_in_ratio.party.W`, `flow.min_since_last_in` | Soma saída ÷ soma entrada (entrada 0 ⇒ `Missing`); minutos desde o último crédito | party | 1h, 24h, 7d | STREAM | 1 |
| 13 | `payee.distinct.party.W`, `payer.distinct.party.W` | Favorecidos/pagadores distintos (HLL, erro ~1%) | party | 24h, 7d | STREAM | 1 |
| 14 | `dict.key_fraud_marks`, `dict.account_fraud_marks`, `med.count.party.90d`, `med.as_recipient.party.90d` | Marcações no DICT da chave/conta destino (**sinal enviado pelo banco**); MEDs envolvendo a parte | request / party | 90 d | Request; `MED_NOTIFICATION` | 0/1 |
| 15 | `party.risk_level`, `party.is_pep`, `party.sanction_status`, `party.csnu_frozen`, `party.edd_pending` | Estado atual de risco/PEP/sanção/indisponibilidade | party | — | Perfil (evento de parte) + memória local para `csnu_frozen` | 0* |
| 16 | `peer.amount_to_p95_30d`, `peer.out_sum_to_p95_30d` | Valor/soma 30 d ÷ p95 do peer group (segmento × faixa de renda × UF) | party | 30 d | BATCH diário (PG) → perfil | 0* |
| 17 | `capacity.out_sum_30d_to_income` | Saídas 30 d ÷ renda/faturamento mensal declarado | party | 30 d | BATCH/perfil | 0* |
| 18 | `cash.in.count/sum/max.party.W` | Depósitos em espécie: nº, soma, maior valor | party | 24h, 7d, 30d | STREAM (7d) / BATCH (30d) | 1 |

`0*` = vem do hash de perfil da parte, lido uma única vez por decisão (todas as features de perfil custam 1 operação no total). Custo = comandos Redis no pipeline.

### 5.3 Estruturas Redis (escolha e justificativa)

| Janela | Estrutura | Precisão | Memória por entidade ativa |
|---|---|---|---|
| 1m, 10m, 1h | **Sorted set** `…:z:<métrica>` — membro `eventId:valorCentavos`, score = epoch ms; `ZREMRANGEBYSCORE` na escrita; teto 500 membros (acima ⇒ feature marca `SATURATED`, que é sinal por si só) | **Exata** | ~60 B × eventos/h (tipicamente < 2 KB) |
| 24h | **Hash de buckets horários** `…:h` campos `<métrica>|<hora>` | Janela efetiva 24–25 h (bucket mais antigo inteiro — **erro conservador**, superconta) | 24 × nº métricas × ~12 B (listpack) |
| 7d, 30d | **Hash de buckets diários** `…:d` | 7–8 d / 30–31 d | idem, 30 campos |
| Distintos | **HyperLogLog** por dia (`PFADD`/`PFCOUNT` multi-chave) | ±0,81% | esparso: < 300 B com baixa cardinalidade |
| Relacionamento/dispositivos | **Hash** `rel:<party>` campo `cpHash → firstDay,lastDay,count` | Exata | ~40 B por favorecido (teto 2.000; LRU) |
| Perfil | **Hash** `prof:<party>` (ticket médio, t-digest p95, histograma horário, UFs, idade, risco, PEP...) | Snapshot | ~1 KB |

**Por que não só sorted set**: em 30 d, uma parte com 3.000 operações custaria ~180 KB; buckets custam < 2 KB. **Por que não só buckets**: velocity de 1 min com bucket de 1 min tem erro de 100%. Híbrido dá exatidão onde fraude precisa (curto prazo) e memória previsível no resto.
Estimativa: tenant com 2 mi partes ativas × ~4 KB ≈ **8 GB** + contrapartes/dispositivos/IPs (~50%) ≈ 12 GB, réplicas à parte. Configurar `hash-max-listpack-entries 512`.

**Chaves e cluster**: `cg:{t:<tenantId>:<ent>:<id>}:<estrutura>` — hash tag inclui tenant+entidade ⇒ tudo de uma entidade num slot; o pipeline agrupa por nó e dispara em paralelo (Lettuce). **TTLs**: sorted set 2 h; buckets horários 26 h; diários 32 d; HLL diário 8 d; rel 400 d (renovado na escrita); perfil 35 d; idempotência 24 h.

**Escrita** (STREAM e SYNC-tentativa): script Lua atômico por entidade — dedup `SET NX …:seen:<eventId>` (TTL 48 h) + `ZADD` + `HINCRBY`/`HINCRBYFLOAT` (centavos inteiros) + `PFADD` + `EXPIRE`. Reentrega Kafka não duplica contagem.

### 5.4 Backfill, consistência e perda de dados
- **Backfill** por tenant (`FeatureBackfillJob`): lê `transactions` da partição/janela (7 d para janelas, 400 d para relacionamento, 90 d para perfil) em lotes, recalcula e grava com `eventId` (idempotente). Usado em: onboarding de tenant, nova feature, recuperação.
- **Marca d'água**: `cg:{t:<tenant>}:fs:epoch` gravada ao fim do backfill. Se ausente (Redis perdeu dados/failover sem réplica), o FeatureStore devolve `Missing(STORE_RESET)` para features de janela em vez de **zero** — contagem zero silenciosa gera falso negativo. Regras aplicam `onMissing`; métrica + alerta operacional; backfill disparado automaticamente.
- Redis Cluster com réplica por shard + AOF `everysec` (perda máxima ~1 s em failover); o STREAM reprocessa do offset, a dedup evita dupla contagem.
- Postgres continua sendo a **fonte da verdade**; Redis é derivado e reconstruível. O BATCH nunca lê Redis.

### 5.5 Isolamento e quotas por tenant
- `tenantId` vem **só** do `TenantContext` (token); o FeatureStore monta todas as chaves; nenhuma API recebe chave crua. Teste obrigatório: decisão do tenant A nunca lê/escreve chave com prefixo de B (`DecisionTenantIsolationIT`).
- Quotas: entidades ativas e bytes estimados por tenant (amostragem `MEMORY USAGE` + contadores) com alarme a 80%; limite de escrita por tenant no consumidor. Tenants grandes podem ter **cluster Redis dedicado** (roteamento por tenant na factory de conexões) — decisão comercial (§14).

## 6. Linguagem de regras (regras como dados)

### 6.1 Escolha da tecnologia

| Critério | **DSL JSON própria (AST)** | CEL (cel-java) | Drools | SpEL |
|---|---|---|---|---|
| Segurança/sandbox | Total: gramática fechada, sem chamada de método | Boa (não Turing-completa, sem efeitos) | Fraca (MVEL/Java em regras) | Fraca: `T()`/métodos; `SimpleEvaluationContext` reduz mas é frágil |
| Latência | Excelente (AST → closures) | Excelente (programa compilado) | Boa, mas Rete/sessão pesa e aquece devagar | Boa |
| Explicabilidade (valor observado por condição) | **Nativa**: cada nó tem `id`, trilha trivial | Exige instrumentar subexpressões | Difícil | Difícil |
| Builder no-code (ida e volta) | **Nativa**: o builder edita a própria AST | Texto ↔ árvore: parser no front | Não | Não |
| Validação estática (tipos, custo, features) | Própria, sob medida | Type-checker bom; custo exige extensão | Parcial | Não |
| Maturidade | A construir (escopo pequeno) | Alta (Google, Kubernetes) | Alta, mas pesada | Alta, inadequada a usuário externo |

**Recomendação**: **DSL JSON própria** como formato canônico armazenado e editado pelo builder, interpretada por avaliador Java compilado para closures. CEL fica como possível **"expressão avançada"** futura (nó `cel` restrito, Fase 3) — decisão em aberto §14. Drools e SpEL **descartados** (execução arbitrária / explicabilidade).

### 6.2 Gramática (`dslVersion: 1`)
```
Rule        := { dslVersion, code, name, category(FRAUD|AML|CFT), carta4001?, locked?,
                 appliesTo:{ eventTypes[], channels[]?, products[]?, riskLevels[]? },
                 paths[](SYNC|STREAM|BATCH), params:{ nome:{type, default, min?, max?} },
                 when: Cond, then: Then, onMissing: NO_MATCH|MATCH|DEGRADE,
                 dedup?:{ key: party|cp|pixkey, window } }
Cond        := {all:[Cond+]} | {any:[Cond+]} | {not:Cond} | {atLeast:n, of:[Cond+]} | Pred
Pred        := { id, op, left: Operand, right?: Operand }
op          := eq ne gt gte lt lte between in notIn inList notInList isTrue isFalse isMissing
Operand     := {feature:id} | {field:caminho} | {param:nome} | {const:valor}
             | {mul|div|add|sub:[Operand,Operand]} | {min|max:[Operand+]}
Then        := { outcome: APPROVE|REVIEW|HOLD|BLOCK, scoreDelta: -1000..1000,
                 reasonCode, severity: LOW|MEDIUM|HIGH|CRITICAL, actions[] }
```
Semântica: `div` por zero ⇒ `Missing`; qualquer `Missing` num `Pred` torna o predicado **desconhecido** (lógica de 3 valores: `all` com desconhecido e sem falso ⇒ desconhecido; `any` com verdadeiro ⇒ verdadeiro); resultado desconhecido da regra aplica `onMissing`. Dinheiro em centavos `long`; `field` só do subconjunto normalizado (`amount`, `currency`, `channel`, `pix.initiation`, `counterparty.country`, `counterparty.ispb`, `cash`, `hourLocal`...). Sem loops, sem strings livres em comparação de PII, sem regex.

### 6.3 Seis regras reais

**R1 — Conta laranja/passagem PIX** (fraude + PLD, SYNC):
```json
{"dslVersion":1,"code":"FR_MULE_01","name":"Conta de passagem PIX","category":"FRAUD","carta4001":"IV",
 "appliesTo":{"eventTypes":["PIX_OUT"]},"paths":["SYNC","STREAM"],
 "params":{"minIn1h":{"type":"MONEY","default":"3000.00"},"ratio":{"type":"DECIMAL","default":"0.8"},"maxAge":{"type":"INT","default":90}},
 "when":{"all":[
   {"id":"c1","op":"lt","left":{"feature":"party.account_age_days"},"right":{"param":"maxAge"}},
   {"id":"c2","op":"gte","left":{"feature":"txn.in.sum.party.1h"},"right":{"param":"minIn1h"}},
   {"id":"c3","op":"gte","left":{"add":[{"feature":"pix.out.sum.party.1h"},{"field":"amount"}]},"right":{"mul":[{"feature":"txn.in.sum.party.1h"},{"param":"ratio"}]}},
   {"atLeast":1,"of":[
     {"id":"c4","op":"gte","left":{"feature":"payer.distinct.party.24h"},"right":{"const":5}},
     {"id":"c5","op":"lte","left":{"feature":"flow.min_since_last_in"},"right":{"const":15}}]}]},
 "then":{"outcome":"HOLD","scoreDelta":400,"reasonCode":"FR_MULE_PASS_THROUGH","severity":"HIGH","actions":["DELAY_PIX","OPEN_ALERT"]},
 "onMissing":"NO_MATCH"}
```
**R2 — ATO após troca de dispositivo** (SYNC):
```json
{"code":"FR_ATO_01","category":"FRAUD","appliesTo":{"eventTypes":["PIX_OUT","TED_OUT"]},"paths":["SYNC"],
 "params":{"minAbs":{"type":"MONEY","default":"2000.00"}},
 "when":{"all":[
   {"any":[{"id":"c1","op":"lt","left":{"feature":"device.first_seen_min"},"right":{"const":60}},
           {"id":"c2","op":"lt","left":{"feature":"party.last_sensitive_change_min"},"right":{"const":1440}}]},
   {"id":"c3","op":"isTrue","left":{"feature":"payee.is_new"}},
   {"atLeast":1,"of":[{"id":"c4","op":"gte","left":{"feature":"amount.to_avg_ticket_90d"},"right":{"const":3}},
                      {"id":"c5","op":"gte","left":{"field":"amount"},"right":{"param":"minAbs"}}]}]},
 "then":{"outcome":"HOLD","scoreDelta":420,"reasonCode":"FR_ATO_NEW_DEVICE_NEW_PAYEE","severity":"HIGH","actions":["STEP_UP_AUTH","DELAY_PIX"]},
 "onMissing":"NO_MATCH"}
```
(O `atLeast` com `c5` cobre cliente sem histórico, em que `c4` fica `Missing(INSUFFICIENT_HISTORY)`.)

**R3 — Fracionamento de espécie abaixo do limiar** (STREAM, PLD, Carta grupo I):
```json
{"code":"AML_STRUCT_CASH_01","category":"AML","carta4001":"I","appliesTo":{"eventTypes":["CASH_DEPOSIT"]},"paths":["STREAM","BATCH"],
 "params":{"limiar":{"type":"MONEY","default":"50000.00"},"n":{"type":"INT","default":3}},
 "when":{"all":[
   {"id":"c1","op":"gte","left":{"feature":"cash.in.count.party.7d"},"right":{"param":"n"}},
   {"id":"c2","op":"lt","left":{"feature":"cash.in.max.party.7d"},"right":{"param":"limiar"}},
   {"id":"c3","op":"gte","left":{"feature":"cash.in.sum.party.7d"},"right":{"mul":[{"param":"limiar"},{"const":"0.9"}]}}]},
 "then":{"outcome":"REVIEW","scoreDelta":250,"reasonCode":"AML_STRUCTURING_CASH","severity":"HIGH","actions":["OPEN_ALERT"]},
 "dedup":{"key":"party","window":"7d"},"onMissing":"NO_MATCH"}
```
**R4 — Espécie ≥ R$ 50 mil → COE** (regra **de sistema**, `locked: true`: tenant não desativa nem aumenta o limiar; só pode reduzir):
```json
{"code":"AML_CASH_50K","category":"AML","carta4001":"I","locked":true,
 "appliesTo":{"eventTypes":["CASH_DEPOSIT","CASH_WITHDRAWAL","CASH_WITHDRAWAL_PROVISION"]},"paths":["SYNC","STREAM","BATCH"],
 "params":{"limiar":{"type":"MONEY","default":"50000.00","max":"50000.00"}},
 "when":{"id":"c1","op":"gte","left":{"field":"amountBrl"},"right":{"param":"limiar"}},
 "then":{"outcome":"APPROVE","scoreDelta":0,"reasonCode":"AML_CASH_50K","severity":"HIGH","actions":["COE_AUTOMATIC"]},
 "onMissing":"MATCH"}
```
(Garante a COE nos três caminhos; o BATCH `findCashWithoutCoe` continua como rede de segurança.)

**R5 — PEP com valor relevante** (STREAM; Circ. 3.978, monitoramento reforçado de PEP): `all[ party.is_pep, any[ amount ≥ param (R$ 10.000), amount.to_p95_90d ≥ 2 ] ]` → `REVIEW`, +200, `AML_PEP_RELEVANT_AMOUNT`, `[OPEN_ALERT]` (o case herda **quatro olhos**, 012/017).

**R6 — Contraparte em jurisdição GAFI** (SYNC+STREAM, grupo de atividades internacionais):
```json
{"code":"AML_FATF_01","category":"AML","appliesTo":{"eventTypes":["TED_OUT","FX_OPERATION","PIX_IN","TED_IN"]},"paths":["SYNC","STREAM"],
 "when":{"any":[
   {"id":"c1","op":"inList","left":{"field":"counterparty.country"},"right":{"const":"FATF_CALL_FOR_ACTION"}},
   {"all":[{"id":"c2","op":"inList","left":{"field":"counterparty.country"},"right":{"const":"FATF_INCREASED_MONITORING"}},
           {"id":"c3","op":"gte","left":{"field":"amount"},"right":{"const":"10000.00"}}]}]},
 "then":{"outcome":"REVIEW","scoreDelta":300,"reasonCode":"AML_FATF_JURISDICTION","severity":"HIGH","actions":["OPEN_ALERT"]},
 "onMissing":"NO_MATCH"}
```
(Listas GAFI são **listas de sistema** atualizadas pela plataforma após cada plenária, com vigência.)

### 6.4 Validação estática (no salvar e no ativar)
1. JSON Schema `dsl-v1.schema.json`; 2. tipos de operandos compatíveis com `op`; 3. features existem no catálogo **e** estão disponíveis em todos os `paths` declarados (ex.: `peer.*` só BATCH/perfil ⇒ ok no SYNC pois vem do perfil; `cash.in.*.30d` ⇒ proibido no SYNC); 4. `params` declarados, dentro de `min/max`; 5. listas existem no tenant; 6. limites: profundidade ≤ 8, ≤ 64 nós, ≤ 20 features por regra; **custo da política** ≤ 40 unidades Redis e ≤ 300 regras; 7. ações coerentes (`CONTACT_CUSTOMER_OOB` proibida com categoria AML/CFT; `COE_AUTOMATIC` só em regras de sistema; `BLOCK` em `PIX_IN` proibido — usar `CAUTIONARY_BLOCK_IN`); 8. regra `locked` só altera o que o schema permite.

### 6.5 Avaliação determinística e explicável
- Puro: `f(evento normalizado, FeatureVector, versão da regra) → resultado`; sem relógio (o "agora" vem no evento), sem I/O, sem aleatoriedade ⇒ reexecutável em auditoria e backtesting.
- Trilha por condição: `{"rule":"FR_MULE_01","v":3,"c1":{"obs":12,"thr":90,"r":true},"c3":{"obs":"9100.00","thr":"7200.00","r":true},"c4":{"obs":7,"r":true},"c5":{"r":null,"missing":"STORE_RESET"}}`. Texto legível gerado do template do reason code + valores observados (atende invariante: alerta guarda regra + versão + parâmetros + valores observados).

## 7. Ciclo de vida das regras e políticas

| Estado | O que acontece | Quem | Saída obrigatória |
|---|---|---|---|
| `DRAFT` | Edição livre (builder/JSON) | RULE_EDITOR | — |
| `VALIDATED` | §6.4 passou | sistema | relatório de validação |
| `SIMULATED` | Avalia sobre **amostra** (últimos 7 d, até 100 mil eventos, features reconstruídas do PG) em < 2 min | RULE_EDITOR | disparos/dia estimados, exemplos |
| `BACKTESTED` | Reavalia histórico (padrão 90 d, até 12 meses) por partição, em job assíncrono com **features reconstruídas "as of"** (sem vazamento do futuro) | RULE_EDITOR | alertas/dia, % REVIEW/HOLD/BLOCK, **precisão e recall** contra feedback (`FRAUD_CONFIRMED`, `MED_RECEIVED`, `CASE_COMMUNICATED` = positivo; `FALSE_POSITIVE`, `CASE_ARCHIVED` = negativo; sem rótulo = não rotulado, reportado à parte), sobreposição com regras existentes, diff vs versão campeã |
| `SHADOW` | Em produção sem efeito (avaliada após a resposta), N dias (padrão 7) | SUPERVISOR inicia | comparação campeã × desafiante: concordância, deltas de outcome, FP/TP rotulados no período |
| `PENDING_APPROVAL` | Submetida com justificativa | autor | — |
| `ACTIVE` (com `effectiveFrom/To`) | Aprovada por **outro** usuário (quatro olhos; `approver ≠ author`), entra numa nova **versão de política** | SUPERVISOR/ADMIN | registro de aprovação + auditoria |
| `MONITORED` | Drift: volume diário fora de ±3σ da média de 28 d, taxa de FP > limiar, taxa de `Missing` > 5% ⇒ alerta ao dono da regra | sistema | métricas por regra |
| `INACTIVE` | Desativação com justificativa (quatro olhos para regra `HIGH/CRITICAL` e de sistema; regra `locked` não desativa) | SUPERVISOR | auditoria |

- **Champion/challenger**: a política ativa tem 1 campeã e até 3 desafiantes em shadow por `(eventType, canal)`. Promoção = nova versão de política (quatro olhos). Opcional futuro: *split* de tráfego real (fora da Fase 2 — risco regulatório de tratar clientes diferente sem justificativa).
- Versões de regra e de política são **imutáveis**; editar cria nova versão. Cada decisão grava `policy_version_id` + lista `(rule_code, rule_version_id)` avaliadas e disparadas ⇒ reconstrução exata de "qual versão decidiu".
- Ativação/desativação publica `cactus.policy.activated` (§11.3).
- Migração dos 13 cenários Java: viram **templates de sistema** DSL; rodam em **shadow batch** lado a lado com o `RuleEngineService` até paridade (≥ 99% de concordância em alertas sobre 90 d de backtest); diferenças explicadas por escrito; então o `switch` é removido.

## 8. Score

- **Fase 2 (regras)**: `bruto = Σ scoreDelta` das regras disparadas (pode ter negativos, ex.: favorecido confiável há > 1 ano: −150); `score = clamp(base_tenant + bruto, 0, 1000)` (base padrão 100).
- **Decisão final** = `max(outcome mais severo entre regras disparadas, mapa_de_threshold(score))`, com thresholds por `tenant × eventType × canal` (padrão: < 400 APPROVE; 400–649 REVIEW; 650–849 HOLD; ≥ 850 BLOCK). Listas BLOCK/CSNU dominam tudo. Lista ALLOW **não** anula regras AML/CFT nem de sistema (só reduz score de fraude, com auditoria).
- **Reason codes** ordenados por contribuição (`scoreDelta`; regras que impuseram o outcome sem delta vêm primeiro); no máximo 5 na resposta, todos em `decisions`.
- **Fase 3 (ML)**: modelo exposto como feature `ml.fraud_prob` (ONNX na JVM, 048 §4) com reason codes próprios (contribuições SHAP pré-agregadas). Composição recomendada: regressão logística calibrada (Platt/isotônica) sobre `[ml.fraud_prob, Σ deltas por categoria]` ⇒ `score = round(1000 × p)` — score passa a significar probabilidade calibrada por tenant. Regras de **bloqueio duro** e de PLD continuam fora do modelo. Interface já prevista: `ScoreComposer` (§13.2).

## 9. Cenários cobertos na Fase 2

### 9.1 Fraude
| Cenário | Caminho | Features/sinais principais | Depende de dado externo? |
|---|---|---|---|
| Conta laranja/mula (recebedora) | SYNC (pagamento de saída) + STREAM (recebimentos) | 1, 3, 5, 12, 13 | Não (melhora com DICT) |
| Account takeover | SYNC | 7, 8, 9, 2, 5, 11 | Device fingerprint robusto (**porta**); sem ele, só `device.id` do app do banco |
| Identidade sintética | SYNC em `ONBOARDING` + STREAM | 4 (dispositivo/IP compartilhado entre cadastros), idade × renda declarada | Bureau/biometria facial (**porta**); Fase 2 cobre só o lado comportamental |
| Golpe com engenharia social (vítima transfere) | SYNC | 5 (favorecido novo), 11, `context.onCall`/sessão, chave criada há poucos dias (DICT), 10 | Sinais de sessão/chamada vêm do app do banco; biometria comportamental (**porta**) |
| Falso boleto | SYNC `BOLETO_PAYMENT` | beneficiário novo (5), valor × histórico, lista de beneficiários denunciados (lista tenant) | Base de beneficiários do boleto (CIP) — **porta**, não disponível na Fase 2 |
| MED/devoluções repetidas | STREAM + SYNC | 14, 3 | Eventos MED enviados pelo banco |
| Velocity / primeira transação / horário atípico / geo | SYNC | 1, 2, 5, 9, 10 | IP geo: base GeoIP (licença — §14) |

### 9.2 PLD — grupos da Carta Circular 4.001/2020
A 009 lista os grupos nominalmente e de forma incompleta ("VII a XVII"). **Pré-requisito da trilha E**: compliance conferir e completar a enumeração dos 17 incisos na 009 (*validar com compliance*). Mapeamento proposto sobre o que a 009 já registra:

| Grupo (009) | Caminho | Status de dados |
|---|---|---|
| I Espécie (moeda nacional), inclusive COE ≥ 50 mil e provisionamento | SYNC (COE) + STREAM + BATCH | Temos (`cash`, `cashWithdrawalProvision`) |
| II Moeda estrangeira em espécie / pré-pagos | STREAM + BATCH | Parcial: falta evento de carga/recarga de pré-pago |
| III Identificação/qualificação | BATCH + evento `ONBOARDING` | Temos cadastro; "resistência/informação falsa" depende de sinal do banco |
| IV Contas de depósito e pagamento (perfil, arredondados, passagem, fracionamento) | STREAM + BATCH (+ SYNC para passagem) | Temos |
| V Investimentos | BATCH | **Não temos** eventos de investimento (ingestão nova) |
| VI Crédito | BATCH | **Não temos** contratos/liquidações de crédito |
| Recursos de contratos com setor público | BATCH | **Não temos** (CNPJ de órgão público / lista) |
| Consórcios | BATCH | **Não temos** |
| Terrorismo/CSNU | SYNC (bloqueio) + STREAM | Temos screening; bloqueio pendente (003/011) |
| Atividades internacionais / GAFI | SYNC + STREAM | Temos país da contraparte; lista GAFI a carregar |
| Funcionários, parceiros, prestadores | BATCH | Parcial (KYE/KYP existem) |
| Campanhas eleitorais | BATCH | **Não temos** (CNPJ de campanha/TSE) |
| Municípios de risco | BATCH | Falta lista oficial/parametrizada de municípios |

Grupos sem dados: a regra-template é criada e **desativada com motivo "fonte de dados ausente"**, visível no relatório de cobertura (honestidade com o regulador e com o cliente).

### 9.3 Portas de integração (provider ports)
Princípio: **sinal no request** é preferível a chamada síncrona (o banco já consulta DICT e provedores de dispositivo no fluxo dele). O CactusGuard **não é participante do PIX** ⇒ não acessa o DICT diretamente; dados do DICT chegam pelo banco (*validar com jurídico o compartilhamento das informações de segurança do DICT com fornecedor*).

| Porta (`decision/provider/port`) | Contrato | Modo |
|---|---|---|
| `DictSecuritySignalsPort` | `Optional<DictSignals> lookup(tenant, pixKeyHash \| accountHash)` | Sinal no request; adaptador opcional via API do banco, timeout 30 ms, cache 10 min |
| `DeviceIntelligencePort` | `DeviceSignals assess(tenant, deviceRef, sessionRef)` | Sinal no request; adaptador por provedor |
| `IpIntelligencePort` | `IpSignals resolve(ip)` (país/UF/ASN/anonimizador) | **Local** (base embarcada, atualização semanal) — sem rede |
| `BehavioralBiometricsPort` | `BehaviorSignals score(tenant, sessionRef)` | Sinal no request |
| `FraudLabelIngestionPort` | MED, chargeback, denúncias | Assíncrono (`/decision-events`) |

## 10. Persistência e auditoria

### 10.1 Tabelas (migrations reservadas em §13.4)
- **`decisions`** — `PARTITION BY RANGE (decided_at)` mensal, PK `(tenant_id, id, decided_at)`, RLS `ENABLE+FORCE` em todas as partições (padrão V48–V50/047). Colunas: `id`, `tenant_id`, `idempotency_key`, `path` (SYNC/STREAM/BATCH), `event_type`, `channel`, `product`, `party_id`, `occurred_at`, `decided_at`, `outcome`, `score`, `degraded`, `degraded_reasons text[]`, `policy_version_id`, `rules_evaluated jsonb` (código+versão), `rules_fired jsonb` (trilha §6.5), `features_used jsonb` (**só** as lidas, com valor e `asOf`), `reason_codes text[]`, `actions text[]`, `input jsonb` (normalizado, só hashes/enums/valores), `input_digest`, `latency_ms`, `latency_breakdown jsonb`, `shadow boolean`. Índices: `(tenant_id, party_id, decided_at)`, `(tenant_id, outcome, decided_at) WHERE outcome <> 'APPROVE'`.
- `decision_idempotency_keys (tenant_id, key, day)` UNIQUE — rede de segurança do §3.5.
- `decision_shadow_results` — mesma forma, só desafiantes (particionada).
- `decision_feedback` — rótulos com `source`, `labeled_at`, autor; append-only.
- `decision_policies`, `decision_policy_versions` (imutável: regras+versões, thresholds, degradação, `approved_by ≠ created_by`), `decision_lists`, `decision_list_entries` (valor **hash**, vigência, motivo, autor), `service_clients`.
- `monitoring_rules` ganha `definition jsonb`, `dsl_version`, `engine` (`JAVA_LEGACY|DSL`), `paths text[]`, `event_types text[]`, `locked`; `monitoring_rule_versions` guarda o snapshot de `definition`. **Decisão**: não criar catálogo de regras paralelo — alertas, UI 029 e quatro olhos já apontam para `monitoring_rules`.

### 10.2 Escrita durável sem pagar o custo do Postgres no SYNC
O outbox Postgres (047) existe para atomicidade entre estado do banco e evento; a decisão SYNC **não altera estado do banco** — o próprio evento é o registro. Proposta: **log-first**: publicar `cactus.decision.made` em `cactus.decisions.v1` com produtor idempotente `acks=all`, `min.insync.replicas=2`, aguardando confirmação (≤ 20 ms) **antes** de responder; consumidor `DecisionPersistenceConsumer` grava em `decisions` (lote, idempotente por `decisionId`). Kafka indisponível/timeout ⇒ fallback: `INSERT` em `decisions` + `outbox_events` na mesma transação (caminho já existente), marcando `latency_breakdown.fallback=pg`. Nenhuma decisão respondida fica sem registro durável. (Alternativa conservadora: outbox PG sempre — §14, decisão 2.)
- STREAM/BATCH: decisões gravadas na transação do consumidor/job + outbox (padrão 047).
- Payload do evento: ids, enums, score, outcome, reason codes, versões — **sem PII** (`PiiGuard`). Chave Kafka = `party_id` (ordem por parte).

### 10.3 Retenção
Decisões são registro de procedimento de monitoramento e de operação ⇒ **10 anos** para todas (interpretação conservadora, 003/Circ. 3.978); nenhuma partição removida. `decision_shadow_results`: proposta de 2 anos (não produziram efeito) — **requer aprovação** (§14). Feedback e versões de regra/política: 10 anos. Redis: só derivados, TTLs do §5.3.

### 10.4 Decisão → alerta → case
- `DecisionAlertingConsumer` (grupo próprio em `cactus.decisions.v1`): para `OPEN_ALERT` ou outcome ≥ REVIEW de regra `AML/CFT` ⇒ `AlertCreationService` com `rule_id`, `rule_version`, parâmetros, valores observados, `decision_id`; dedup por `(tenant, party, rule, janela dedup)`. Fraude `HOLD/BLOCK` gera alerta na fila de fraude (tipo distinto, SLA de minutos).
- Data de **seleção** (prazo de 45 d de análise, 003) = `decided_at` da decisão que gerou o alerta.
- `COE_AUTOMATIC` ⇒ fluxo de comunicação automática existente (sem análise); `FREEZE_ASSETS_CSNU` ⇒ fluxo CSNU.
- Decisões `degraded` reavaliadas no STREAM: se a política completa teria disparado, gera alerta `DEGRADED_MISS`.

## 11. Não-funcionais

### 11.1 Capacidade
| Métrica | Alvo Fase 2 |
|---|---|
| Throughput por tenant | 300 decisões/s sustentado, 1.000 pico (cota contratável) |
| Throughput total | 3.000/s sustentado, 6.000/s pico com 6 instâncias de 4 vCPU |
| Escala | App **stateless** (estado só em Redis/Kafka/PG + caches de política/listas recarregáveis); HPA por CPU e p99; Redis Cluster ≥ 3 primários + réplicas; partições de `cactus.decisions.v1` = 24 |
| Disponibilidade da Decision API | 99,95% mensal (decisão degradada conta como disponível; 5xx/timeout não) |

### 11.2 Cache de política e invalidação
- Snapshot por tenant em memória: política ativa por `(eventType, canal)` **compilada** + desafiantes + próxima versão agendada (`effectiveFrom`) ⇒ troca exata no instante, sem consulta.
- Invalidação: evento `cactus.policy.activated` em `cactus.policies.v1`, consumido por **todas** as instâncias (group id único por instância, `auto.offset.reset=latest`) ⇒ recompila e troca atomicamente. Reconciliação a cada 60 s (compara `version` por tenant em uma consulta leve) cobre evento perdido. Na subida, instância só fica `ready` com todas as políticas e listas carregadas.
- Mesmo mecanismo para listas (`cactus.list.changed`) e CSNU.

### 11.3 Observabilidade (038)
- Spans: `decision.http`, `.auth`, `.idempotency`, `.policy`, `.lists`, `.features` (atributo: nº de comandos, nós), `.provider.<nome>`, `.rules`, `.score`, `.publish`. Sem PII em atributos.
- Métricas: `cactus.decision.latency` (histograma por `event_type`, `outcome`, `path`; tenant como tag apenas para os top-N contratuais), `cactus.decision.stage.latency{stage}`, `cactus.decision.degraded{reason}`, `cactus.decision.outcome`, `cactus.feature.missing{feature,reason}`, `cactus.rule.fired{rule}`, `cactus.policy.version{tenant}`, `cactus.featurestore.write.lag` (atraso STREAM).
- SLOs: p99 SYNC < 100 ms (janela 28 d); taxa `degraded` < 0,1%; atraso STREAM p99 < 5 s; backlog de persistência de decisões < 60 s.

### 11.4 Testes
| Tipo | O quê |
|---|---|
| Unitário DSL | Cada operador, lógica de 3 valores, `div` por zero, `onMissing`, ordenação de reason codes, regras `locked` |
| Propriedade/fuzz (jqwik) | AST aleatória: validador nunca lança exceção não tratada; avaliação determinística (mesma entrada ⇒ mesma saída/trilha); toda AST aceita respeita limites de custo; round-trip JSON ↔ AST |
| Contrato | OpenAPI da Decision API versionada; testes de schema de request/response; catálogo de reason codes estável |
| IT (Testcontainers Redis + Kafka + PG) | Velocity correta nas bordas de janela; reentrega Kafka não duplica; perda do Redis ⇒ `STORE_RESET` ⇒ backfill; log-first + fallback PG; invalidação de política em 2 instâncias; **isolamento de tenant** (PG + chaves Redis) |
| Carga (k6, `constant-arrival-rate`, evita omissão coordenada) | Ambiente fixado (3 instâncias 4 vCPU, Redis 3+3, Kafka 3 nós, PG 17); 1 mi partes, features pré-populadas; política com 150 regras + 40 features; **aquecimento 5 min descartado**, **15 min em regime** a 2.000/s, **5 repetições**; critério: p99 < 100 ms em **todas** as repetições, mediana dos p99 < 80 ms, erro < 0,1%, `degraded` < 0,1%; resultado (com versão, hardware e histograma) registrado na 041 |

## 12. Segurança e conformidade
- **Sem PII** em features, chaves Redis, logs, spans, eventos e `decisions.input`: só hashes HMAC (040), ids internos, enums e valores. Documento/chave/IP em claro só em memória durante a requisição. Teste automatizado varre chaves Redis e payloads com o `PiiGuard`.
- **LGPD**: base legal proposta — prevenção à fraude e segurança do titular (art. 11, II, "g", para dado sensível como biometria) e cumprimento de obrigação legal/regulatória para PLD (art. 7º, II) e legítimo interesse/proteção do crédito onde couber (art. 7º, IX/X). **Validar com jurídico** antes de ativar sinais biométricos e de dispositivo. Decisão automatizada: art. 20 (direito de revisão) — o fluxo REVIEW/HOLD com analista atende; documentar.
- **Explicabilidade para BCB**: toda decisão reconstruível (versão de política/regra, parâmetros, valores observados, features com `asOf`); relatório por regra (racional, cenário da Carta, backtest, aprovações).
- **Tipping-off**: a API responde ao **sistema do banco**, nunca ao cliente final; reason codes AML/CFT marcados `confidential`; `customerMessageCode` genérico; ação de contato com cliente vetada quando há motivo AML/CFT; endpoints de consulta exigem role interna. Cláusula contratual obrigatória.
- Auditoria (`AuditService`): criação/edição/aprovação/ativação/desativação de regra e política, alteração de degradação/thresholds, listas, emissão/rotação de credenciais, consulta de decisão com trilha.
- Regras de sistema (`locked`): COE ≥ 50 mil e CSNU não podem ser desligadas pelo tenant.

## 13. Plano de implementação em trilhas

### 13.1 Ondas e propriedade de arquivos
Raiz do novo módulo: `br.com.cactus.guard.decision` (hexagonal, 047). **Cada pacote/arquivo tem um único dono por onda.** Arquivos compartilhados são tocados **só** pela Trilha 0.

| Onda | Trilha | Dono de (exclusivo) | Depende de |
|---|---|---|---|
| 0 (1 agente, curta) | **T0 — Contratos** | `decision/core/**` (§13.2), `src/main/resources/decision/feature-catalog-v1.yaml`, `decision/dsl-v1.schema.json`, `src/test/java/.../decision/testkit/**` (fakes: `InMemoryFeatureStore`, `StaticPolicyRepository`, `FixedClockEvents`), constantes em `EventTopics`/`DomainEventType`, dependências no `pom.xml` (jqwik, Testcontainers Redis), `application.yml` (só `spring.config.import` de `decision-rules.yml`, `decision-features.yml`, `decision-api.yml`) | — |
| 1 | **A — Regras e políticas** | `decision/dsl/**` (AST, parser, validador, compilador, trilha), `decision/policy/**` (`PolicyRepository` com cache/invalidação, entidades de política), `rule/**` (extensão de `MonitoringRule`/versões, API `/decision-rules`, `/decision-policies`), `decision-rules.yml`, migrations V60–V61 | T0 |
| 1 | **B — Features, listas e provedores** | `decision/feature/**` (Redis, Lua, catálogo, backfill, perfil batch), `decision/list/**` (listas + cache local + CSNU em memória), `decision/provider/**` (portas + adaptadores fake/locais), `event/consumer/**` (atualização de features no STREAM), `decision-features.yml`, migrations V62–V63 | T0 |
| 1 | **C — API e orquestração** | `decision/api/**`, `decision/service/**` (`DecisionService`, `ScoreComposer`, degradação, idempotência), `decision/persistence/**` (log-first, fallback, `DecisionPersistenceConsumer`), `decision/alerting/**`, `auth/client/**` (client credentials), `config/SecurityConfig` (só rota/filtro novos), `decision-api.yml`, migrations V64–V66 | T0 (usa fakes até A/B entrarem) |
| 2 | **D — Laboratório** | `decision/lab/**` (simulação, backtesting "as of", shadow, champion/challenger, drift), endpoints `/decision-rules/{id}/simulate|backtest|shadow`, migration V67–V68 | A, B, C |
| 2 | **E — Cenários** | `src/main/resources/decision/templates/**` (13 cenários migrados + fraude PIX + grupos Carta), seed V69, `rule/engine/**` (paridade e, ao final, remoção do `switch`), `RuleScheduler` → BATCH sobre a DSL | A, B, C; 009 completada por compliance |
| 2 | **F — Builder web** | `cactusguard-web/src/features/decision-rules/**`, `.../decision-policies/**`, `.../decision-lists/**` | contratos REST de A e D publicados no OpenAPI |
| 3 (1 agente) | **G — Carga e cutover** | `loadtest/decision/**`, ajustes de tuning (só configs), resultado na 041, atualização de 048/README | todas |

### 13.2 Interfaces fixadas (T0 entrega exatamente isto; mudança exige alterar esta spec)
```java
package br.com.cactus.guard.decision.core;

public enum EventType { PIX_OUT, PIX_IN, TED_OUT, TED_IN, BOLETO_PAYMENT, CARD_AUTH, CASH_DEPOSIT,
  CASH_WITHDRAWAL, CASH_WITHDRAWAL_PROVISION, FX_OPERATION, LOGIN, ONBOARDING, SENSITIVE_DATA_CHANGE,
  PIX_KEY_REGISTRATION, PIX_KEY_PORTABILITY, LIMIT_INCREASE, PAYEE_ADD, MED_NOTIFICATION, CHARGEBACK }
public enum Outcome { APPROVE, REVIEW, HOLD, BLOCK }              // ordinal = severidade
public enum Path { SYNC, STREAM, BATCH }
public enum Action { STEP_UP_AUTH, DELAY_PIX, CAUTIONARY_BLOCK_IN, REDUCE_LIMIT, CONTACT_CUSTOMER_OOB,
  OPEN_ALERT, COE_AUTOMATIC, FREEZE_ASSETS_CSNU }
public enum MissingReason { UNKNOWN_PARTY, INSUFFICIENT_HISTORY, STORE_RESET, TIMEOUT, NOT_PROVIDED, DIV_BY_ZERO, SATURATED }

/** Evento normalizado: só hashes/ids/enums/valores. `now` é o relógio da decisão (determinismo). */
public record DecisionEvent(UUID tenantId, UUID eventId, String idempotencyKey, EventType type, Path path,
    String channel, String product, UUID partyId, Instant occurredAt, Instant now,
    Map<String, Object> fields, Map<String, Object> signals) {}

public sealed interface FeatureValue {
  record Num(BigDecimal value) implements FeatureValue {}
  record Bool(boolean value) implements FeatureValue {}
  record Code(String value) implements FeatureValue {}
  record Missing(MissingReason reason) implements FeatureValue {}
}
public record FeatureVector(Map<String, FeatureValue> values, Instant asOf) {
  public FeatureValue get(String featureId) { return values.getOrDefault(featureId, new FeatureValue.Missing(MissingReason.NOT_PROVIDED)); }
}

public interface FeatureStore {
  /** Uma ida por nó Redis; nunca lança por indisponibilidade: devolve Missing(TIMEOUT|STORE_RESET). */
  FeatureVector fetch(DecisionEvent event, Set<String> featureIds, Duration timeout);
  /** Idempotente por eventId. SYNC: tentativas (assíncrono); STREAM: liquidadas. */
  void record(DecisionEvent event, boolean settled);
}
public interface ListMatcher {           // memória local; latência < 0,2 ms
  boolean isBlocked(UUID tenantId, String listKind, String valueHash);
  boolean isCsnuFrozen(UUID tenantId, UUID partyId);
}

public record ConditionTrace(String conditionId, Object observed, Object threshold, Boolean result, MissingReason missing) {}
public record FiredRule(String code, UUID ruleVersionId, int version, Outcome outcome, int scoreDelta,
    String reasonCode, String category, boolean confidential, Set<Action> actions, List<ConditionTrace> trace) {}
public record EvaluationResult(UUID policyVersionId, List<FiredRule> fired, List<UUID> evaluatedRuleVersionIds,
    Set<String> featuresRead, boolean anyMissingRequired) {}

public interface CompiledPolicy {
  UUID policyVersionId(); UUID tenantId(); int version();
  Set<String> requiredFeatures();          // união das features das regras; usado no fetch
  ScoreConfig scoreConfig();               // base, thresholds por outcome
  DegradationConfig degradationConfig();   // por EventType: FAIL_OPEN|FAIL_CLOSED, outcome, degradedAmountLimit
}
public record ScoreConfig(int base, int reviewFrom, int holdFrom, int blockFrom) {}
public record DegradationConfig(Map<EventType, DegradationMode> modes) {}
public record DegradationMode(boolean failClosed, Outcome fallbackOutcome, BigDecimal degradedAmountLimit) {}

public interface RuleEvaluator {          // puro, sem I/O, thread-safe
  EvaluationResult evaluate(CompiledPolicy policy, DecisionEvent event, FeatureVector features);
}
public interface PolicyRepository {       // leitura sempre da memória
  Optional<CompiledPolicy> active(UUID tenantId, EventType type, String channel, Instant at);
  List<CompiledPolicy> challengers(UUID tenantId, EventType type, String channel);
  CompiledPolicy byVersion(UUID tenantId, UUID policyVersionId);   // backtesting/auditoria (pode ir ao PG)
}
public interface ScoreComposer {
  ScoredOutcome compose(CompiledPolicy policy, EvaluationResult result);  // Fase 3 inclui ML
}
public record ScoredOutcome(Outcome outcome, int score, List<FiredRule> orderedReasons, Set<Action> actions) {}

public record Decision(UUID decisionId, UUID tenantId, DecisionEvent event, ScoredOutcome result,
    EvaluationResult evaluation, FeatureVector features, boolean degraded, List<String> degradedReasons,
    Map<String, Long> latencyMicrosByStage, boolean replayed) {}
public interface DecisionService {
  Decision decide(DecisionEvent event, Duration deadline);    // SYNC
  Decision evaluateAsync(DecisionEvent event);                // STREAM/BATCH (sem deadline duro)
}
public interface DecisionRecorder {     // C implementa; D usa para shadow
  void record(Decision decision);       // log-first + fallback (§10.2)
}
```
Normalização de `fields` (chaves e tipos) é definida por T0 em `DecisionFields` (constantes) — A só lê por essas constantes; C as preenche.

### 13.3 Critérios de pronto
| Trilha | Pronto quando |
|---|---|
| T0 | Compila; contratos idênticos ao §13.2; catálogo com as 18 famílias do §5.2 validado por schema; fakes com testes; nenhuma lógica de negócio |
| A | 6 regras do §6.3 carregam, validam e avaliam contra `InMemoryFeatureStore` com trilha esperada; jqwik ≥ 10 mil casos verdes; quatro olhos na ativação; invalidação de política provada com 2 contextos; p99 da avaliação de 300 regras < 3 ms em JMH |
| B | Features do §5.2 corretas nas bordas de janela (IT Redis); dedup em reentrega; `STORE_RESET` + backfill; isolamento de chaves por tenant; `fetch` p99 < 15 ms local com 40 features; listas e CSNU em memória com recarga por evento |
| C | Contrato OpenAPI publicado; client credentials + escopo; idempotência (replay/409); degradação por tenant×evento coberta por teste; log-first com fallback PG provado (Kafka pausado); alerta gerado com regra+versão+valores; RLS em `decisions`; sem PII (teste PiiGuard) |
| D | Backtest de 90 d reproduz precisão/recall com rótulos sintéticos conhecidos; shadow não altera resposta nem latência p99 (> 2 ms); relatório champion×challenger |
| E | 13 cenários em DSL com ≥ 99% de paridade em 90 d; cenários fraude PIX ativos como templates; relatório de cobertura Carta 4.001 com lacunas explícitas |
| F | Builder cria regra, valida, simula, envia para aprovação **sem deploy**; aprovação por outro usuário |
| G | Critério de carga do §11.4 cumprido e registrado na 041 ⇒ **saída da Fase 2** |

### 13.4 Migrations reservadas
Última aplicada: V50. Reserva: **A: V60–V61**; **B: V62–V63**; **C: V64–V66**; **D: V67–V68**; **E: V69**. V51–V59 ficam livres para trabalho fora desta fase. Número não usado na reserva **não** é reaproveitado por outra trilha. Toda FK nova segue o padrão composto `(tenant_id, x_id)` (047) e toda tabela nova tem RLS `ENABLE+FORCE`.

### 13.5 Auto-revisão: três desenvolvedores em paralelo sem conversar?
- Contratos de tipos e de comportamento (timeout, `Missing`, determinismo, idempotência) fixados em §13.2 e entregues **antes** pela T0 ⇒ A, B e C compilam contra a mesma API e testam com fakes. ✔
- Arquivos compartilhados (`EventTopics`, `DomainEventType`, `pom.xml`, `application.yml`, `SecurityConfig`) têm dono único (T0, exceto `SecurityConfig` = C); cada trilha tem seu `.yml` próprio. ✔
- Migrations em faixas disjuntas. ✔
- Ponto de colisão residual identificado e resolvido: **nomes de features** (A valida, B produz, C solicita) ⇒ catálogo único entregue por T0; ids de campos do evento ⇒ `DecisionFields` da T0. ✔
- Ponto que ainda exige alinhamento humano: semântica regulatória dos grupos da Carta (E) ⇒ dependência explícita de compliance completar a 009. ✔ (registrado)

## 14. Decisões do dono do produto

> **APROVADAS em 2026-11-09 — todas conforme a coluna "Recomendação"** (DSL JSON própria; registro Kafka-primeiro com
> outbox Postgres de reserva; degradação híbrida por tipo de evento; e as demais). Implementação deve seguir a
> recomendação como decisão fechada. Itens marcados "validar com jurídico" continuam pendentes de validação e não
> bloqueiam a implementação, mas devem ser configuráveis.

| # | Decisão | Opções | Recomendação |
|---|---|---|---|
| 1 | Formato de regras | DSL JSON própria / CEL / ambos | **DSL JSON própria**; CEL só como nó "avançado" na Fase 3, se cliente pedir |
| 2 | Registro durável no SYNC | Log-first Kafka (+ fallback PG) / outbox PG sempre | **Log-first** — PG síncrono a milhares/s ameaça o p99 e a capacidade do banco; fallback mantém garantia |
| 3 | Padrões de degradação | Tabela §3.6 / tudo fail-open / tudo fail-closed | **Tabela §3.6**, configurável por tenant com quatro olhos; validar com jurídico o efeito no Regulamento PIX |
| 4 | Retenção | 10 anos para tudo / 10 anos decisões + 2 anos shadow | **10 anos decisões e feedback; 2 anos shadow** (sem efeito sobre cliente) — validar com jurídico |
| 5 | Unificação com `monitoring_rules` | Estender tabela existente / catálogo novo | **Estender** (preserva alertas, UI 029, versões e aprovação) |
| 6 | Base GeoIP | Comercial (licença) / gratuita (precisão menor) / provedor externo | **Comercial com atualização semanal, embarcada** (sem rede no SYNC) |
| 7 | Redis dedicado por tenant | Compartilhado / dedicado como plano premium | **Compartilhado por padrão; dedicado como plano comercial** para bancos grandes |
| 8 | gRPC na Fase 2 | Sim / sob demanda | **Sob demanda** (REST cumpre o orçamento; gRPC economiza ~1–2 ms) |
| 9 | Split de tráfego real (A/B) entre políticas | Permitir / só shadow | **Só shadow na Fase 2** (tratamento diferente de clientes sem justificativa é risco regulatório) |
| 10 | Cotas padrão por tenant | 300/s / outro | **300/s sustentado, 1.000 pico**, contratual acima |
| 11 | Origem dos dados do DICT | Sinal no request / integração via API do banco | **Sinal no request** (fornecedor não é participante do PIX); validar compartilhamento com jurídico |
| 12 | Grupos da Carta sem dados (investimento, crédito, consórcio, setor público, eleitoral) | Ingestão nova na Fase 2 / Fase 4 com template desativado | **Templates desativados com motivo na Fase 2; ingestão priorizada por pipeline comercial** |
| 13 | Limite para tenant reduzir regras de sistema | Tenant pode reduzir limiar COE / nunca altera | **Pode reduzir (mais conservador), nunca aumentar nem desativar** |
