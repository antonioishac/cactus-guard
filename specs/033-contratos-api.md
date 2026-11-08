# Spec 033 — Contratos de API (Principais Endpoints)

## Objetivo
Definir os contratos principais da API REST do CactusGuard que o frontend (Web e Mobile) deve consumir.  
Todos os endpoints são protegidos por JWT (exceto login e refresh).  
Paginação padrão: `page`, `size`, `sort`.  
Respostas de erro seguem o padrão global do backend.

## Autenticação

### POST /api/v1/auth/login
**Request**
```json
{
  "username": "string",
  "password": "string"
}
```
**Response 200**
```json
{
  "accessToken": "string",
  "refreshToken": "string",
  "expiresIn": 1800,
  "user": {
    "id": "uuid",
    "name": "string",
    "roles": ["ANALYST"],
    "permissions": ["alert:read", "case:decide"]
  }
}
```

### POST /api/v1/auth/refresh
**Request**
```json
{ "refreshToken": "string" }
```
**Response 200**: mesmo formato do login (novos tokens).

### POST /api/v1/auth/logout
**Request**: header Authorization  
**Response 204**

---

## Alertas

### GET /api/v1/alerts  (ANALYST, SUPERVISOR, ADMIN, AUDITOR)
**Query**: `status` e `severity` (vários: repetidos ou separados por vírgula), `type`, `assignedTo` (uuid), `from`/`to`
(`AAAA-MM-DD` no fuso de Brasília — `to` inclusivo até o fim do dia — ou ISO-8601; sobre `createdAt`), `partyId`, `ruleCode`,
`page`, `size`, `sort`. **Response 200**: `Page<AlertResponse>`:
`id, partyId, partyName, partyDocument* (mascarado), type, severity, status, title, description, ruleCode, ruleVersion,
assignedToId, dueDate, occurredAt, selectionDeadline, totalAmount, falsePositiveReason, createdAt`. `from` inválido → 422.

### GET /api/v1/alerts/{id}
`AlertResponse` + `evidence[{parameter,label,threshold,observed}]` (do snapshot do disparo), `evidenceSnapshot` (mapa bruto),
`triggeringTransactionIds[]`, `transactions[]` (resumo; documento da contraparte mascarado), `history[{action,fromStatus,toStatus,actorId,actorName,justification,createdAt}]`,
`rule{code,name,version,category,severity,carta4001Reference}` e `party{id,name,type,document*,riskLevel,riskScore,pep,pepStatus,eddRequired,assetFreezeActive,status}`.
Registra `data_access_log` (consulta de dados da parte, mascarados).

### POST /api/v1/alerts/{id}/assume  (ANALYST, SUPERVISOR)
### POST /api/v1/alerts/{id}/escalate  (ANALYST, SUPERVISOR)
`{ "justification": "≥ 20 caracteres não brancos" }` → alerta `ESCALATED` (não é decisão nem encerramento). Alerta encerrado ou
já escalado → 422. Grava histórico (`ESCALATE`) e audita `ALERT_ESCALATED`.
### POST /api/v1/alerts/{id}/false-positive  (ANALYST, SUPERVISOR)
`{ reason (≤100), justification }`. **Response 200**: alerta atualizado.

### GET /api/v1/alerts/{id}/comments  (todas as roles de leitura, inclusive AUDITOR)
### POST /api/v1/alerts/{id}/comments  (ANALYST, SUPERVISOR) → 201
`{ "text": "obrigatório, ≤ 5000" }` → `{ id, authorId, authorName, text, createdAt }`. Grava em `alert_history` (`action=COMMENT`,
sem mudar status) e audita `ALERT_COMMENT_ADDED` (apenas id e tamanho; o texto não vai ao `audit_log`).

---

## Cases

Status: `OPEN, IN_ANALYSIS, ESCALATED, PENDING_APPROVAL, COMMUNICATE (aprovado p/ comunicar), COMMUNICATED, ARCHIVED`
(`CLOSED` da versão anterior da spec não é usado: o encerramento é `ARCHIVED`/`COMMUNICATED`).

### GET /api/v1/cases
**Query**: `status` (vários), `assignedTo`, `slaStatus` (`ON_TIME|DUE_SOON|OVERDUE`), `riskCategory` (nível de risco da parte
`LOW|MEDIUM|HIGH|CRITICAL` ou `PEP`), `isEdd`, `from`/`to` (sobre `createdAt`), `partyId`, `page`, `size`, `sort`.
`CaseResponse`: `id, caseNumber (CG-AAAA-NNNNNN, sequencial por ano), alertId, partyId, partyName, partyRiskLevel, status, priority,
assignedToId, decision, decisionJustification, decidedById, decidedAt, slaDueDate, slaStatus, occurredAt, selectionDeadline,
selectedAt, analysisDeadline, communicationDeadline, escalationLevel, edd, indicatorCodes, totalAmountInvolved, approvedById, approvedAt, closedAt, createdAt`.

**`slaStatus`** é calculado no backend pelo prazo regulatório vigente: em análise (`OPEN, IN_ANALYSIS, ESCALATED, PENDING_APPROVAL`)
vale `analysisDeadline` (seleção + 45 dias; `DUE_SOON` = faltam ≤ 5 dias); com `COMMUNICATE` vale `communicationDeadline`
(D+1 útil da decisão; `DUE_SOON` = faltam ≤ 12 h); vencido = `OVERDUE`. Case encerrado → `slaStatus = null`.

### GET /api/v1/cases/{id}
`CaseResponse` (registra `data_access_log`).

### GET /api/v1/cases/{id}/dossier
Dossiê consolidado: `caseInfo, alert{...,evidence[]}, party{...}, riskHistory[]` (últimos 20), `pep`, `ubos[]` (documento mascarado; cadeia só com
nome/%), `alertTransactions[]`, `periodTransactions[]` (janela `period{from,to}`: período do case ou 90 dias antes da ocorrência até a seleção; máx. 200),
`screeningHits[]`, `evidences[]`, `notes[]`, `auditTrail[]` (até 200 ações do case). Toda PII mascarada; registra `data_access_log`.

### POST /api/v1/cases  (ANALYST, SUPERVISOR) → 201
`{ "alertId": "uuid", "justification": "≥ 20 caracteres não brancos" }`. Seleciona o alerta, gera `caseNumber`, inicia os 45 dias de análise.
A justificativa vai ao `audit_log` (`CASE_CREATED`) e ao histórico do alerta (`CASE_OPENED`).
`POST /api/v1/cases/from-alert/{alertId}` permanece como **alias legado** (corpo opcional `{justification}`).

### POST /api/v1/cases/{id}/decide
Decisão final regulatória (specs 003, 008, 012). Escalar **não** é decisão.
```json
{ "decision": "COMMUNICATE | ARCHIVE", "justification": "string (obrigatória, ≥ 20 não brancos)" }
```
- Sem exigência de quatro olhos (ARCHIVE de parte comum) → `ARCHIVED`.
- `COMMUNICATE`, PEP, risco alto/crítico ou EDD → `PENDING_APPROVAL` (decisão proposta, aguarda aprovação).
- **422**: justificativa ausente; case já decidido/encerrado (não reabre).

### POST /api/v1/cases/{id}/escalate
`{ "justification": "obrigatória", "escalatedToId": "uuid opcional" }` — não encerra o case (`ESCALATED`, +1 nível).

### POST /api/v1/cases/{id}/approve  (SUPERVISOR)
`{ "comment": "opcional" }` (quatro olhos). Aprovador ≠ decisor (422). `COMMUNICATE` → status `COMMUNICATE`, `communicationDeadline` D+1 útil e
rascunho de COS (nunca envio automático); `ARCHIVE` → `ARCHIVED`.

### POST /api/v1/cases/{id}/reject-approval  (SUPERVISOR)
`{ "justification": "obrigatória, ≥ 20 não brancos" }`. Só em `PENDING_APPROVAL`; SUPERVISOR ≠ decisor (422). O case volta a `IN_ANALYSIS`
com a decisão pendente **limpa** (aceita nova decisão); a decisão rejeitada e a justificativa da rejeição ficam no `audit_log`
(`CASE_APPROVAL_REJECTED`, append-only). O decisor é notificado. (Substitui `approved=false` da versão anterior da spec.)

### GET /api/v1/cases/{id}/notes | POST /api/v1/cases/{id}/notes  (POST: ANALYST, SUPERVISOR)
`{ "text": "obrigatório" }` → `{ id, authorId, authorName, text, createdAt }`. Imutáveis; auditoria registra só id/tamanho. Case encerrado não recebe notas.

### GET /api/v1/cases/{id}/evidences | POST /api/v1/cases/{id}/evidences (multipart: `file`, `description?`; POST: ANALYST, SUPERVISOR)
Mesmas validações do KYC: PDF/JPG/PNG (content-type + extensão + assinatura), máx. `cases.evidence.max-bytes` (10 MB), nome sanitizado, sha256 único por case.
Resposta `{ id, fileName, contentType, sizeBytes, sha256, description, uploadedById, uploadedByName, uploadedAt }` (nunca o caminho no servidor).
Arquivos ficam em `cases.evidence.dir` (fora do webroot). Case encerrado não recebe evidências.
**Download**: `GET /api/v1/cases/{id}/evidences/{evidenceId}` (alias `/download`) — revalida o sha256 (divergência → erro e auditoria
`CASE_EVIDENCE_INTEGRITY_FAILURE`), registra `data_access_log` e audita `CASE_EVIDENCE_DOWNLOADED`; `nosniff`.

---

## SISCOAF

### GET /api/v1/siscoaf
`status` e `type` (vários), `from`/`to` (sobre a criação), `partyId`, `caseId`, paginação. **Sem `status` lista todos** (antes: só `DRAFT`).
`SiscoafResponse`: `id, caseId, caseNumber, partyId, partyName, transactionId, originalCommunicationId, type, status, protocolNumber, submittedAt, deadlineAt,
decidedAt, referenceYear, amount, indicatorCodes, periodFrom, periodTo, cancelledAt, cancelReason, createdById, approvedById, approvedAt,
fourEyesRequired, payload, createdAt`. `GET /siscoaf/{id}` acrescenta `statusHistory[{action,status,fromStatus,byId,byName,reason,at}]` (de `siscoaf_history`).

### POST /api/v1/siscoaf/{id}/approve  (SUPERVISOR)
`{ "comment": "justificativa ≥ 20 não brancos" }` (ou `justification`). `DRAFT|PENDING_REVIEW` → `APPROVED`; **não envia**. Se a parte é PEP, risco
alto/crítico ou o case é EDD (`fourEyesRequired`): aprovador ≠ criador do rascunho e ≠ decisor do case (422), e o `submit` de `DRAFT` é bloqueado
(exige aprovação prévia). COS exige case com `COMMUNICATE` aprovado.
### POST /api/v1/siscoaf/{id}/submit  (SUPERVISOR)
Corpo opcional `{ "reason": "≥ 20 caracteres, se informado" }` — gravado em `siscoaf_history` e no `audit_log`. O aprovador original é preservado.
`cancel` / `rectify` continuam com `{reason, payload?}` obrigatório e passam a gravar histórico.

---

## Regras de monitoramento

### GET /api/v1/rules  (paginado)
**Antes: lista simples só de ativas.** Agora `Page<MonitoringRuleResponse>`: padrão = só ativas; `includeInactive=true` inclui todas; `status` (vários, ex.
`PENDING_APPROVAL`) prevalece sobre `includeInactive`; filtros `category`, `severity`, `q` (código/nome).
`MonitoringRuleResponse`: campos anteriores + `createdById/Name, approvedById/Name, approvedAt, carta4001Reference, appliesToRiskLevels, autoCommunication`,
métricas dos últimos 30 dias `alertsGenerated` e `falsePositiveRate` (= falsos positivos / alertas encerrados; `null` se nenhum encerrado).
### GET /api/v1/rules/{code}
Detalhe com as mesmas métricas e `activeVersion` (versão imutável vigente agora). `RuleVersionResponse` ganhou `createdById/Name`, `approvedByName`.
### GET /api/v1/rules/{code}/metrics?from=&to=
`{ code, from, to, alertsGenerated, alertsClosed, falsePositives, falsePositiveRate }` (padrão: últimos 30 dias).

---

## Fila de aprovação

### GET /api/v1/approvals/pending  (SUPERVISOR)
`{ cases: Page, rules: Page, peps: Page, counts{cases,rules,peps} }` (`page`/`size` valem para cada lista; ordem fixa, mais antigos primeiro).
`cases[]`: `id, caseNumber, partyName, partyRiskLevel, decision, decidedById, decidedAt, edd, slaStatus, analysisDeadline, ownDecision`;
`rules[]`: `code, name, category, severity, version, createdById/Name, updatedAt, ownChange`; `peps[]` (PEP vigente sem aprovação da alta administração):
`partyId, partyName, document*, pepCategory, pepRole, pepStartDate, riskLevel, markedAt`. `ownDecision`/`ownChange` indicam itens que o próprio usuário não pode aprovar.

---

## Dashboard (spec 045) — ANALYST, SUPERVISOR, ADMIN, AUDITOR

- `GET /dashboard/operational` (inalterado).
- `GET /dashboard/metrics` → `{ alertsByStatus{OPEN,IN_ANALYSIS,ESCALATED,FALSE_POSITIVE,CLOSED}, screenings (hits pendentes), activePeps, openCases, pendingApproval }`.
- `GET /dashboard/alerts-chart?days=7` (1–90) → `[{date, total}]` com zeros nos dias vazios (dia do servidor/Brasília).
- `GET /dashboard/severity-distribution` → `[{severity, count}]` (alertas em tratamento; sempre as 4 severidades).
- `GET /dashboard/case-sla` → `{ selection, analysis, communication: {onTime,dueSoon,overdue}, pendingApproval }` — seleção (alertas ainda não selecionados,
  45 d da ocorrência), análise (cases em tratamento, 45 d da seleção) e comunicação (cases `COMMUNICATE`, D+1 útil). Todas agregadas em SQL.

## Notificações / Relatórios (complementos)
- `POST /api/v1/notifications/read-all` → `{ updated }` (marca só as do usuário autenticado). `POST /notifications/{id}/read` agora só aceita notificação do próprio usuário.
- `ReportJobResponse.errorMessage`: mensagem genérica quando `FAILED` (detalhe técnico fica apenas no log).

---

## Partes (KYC / PEP)

### GET /api/v1/parties
**Query params** (todos opcionais, combináveis): `q` (full-text; se parecer CPF/CNPJ também casa por HMAC),
`document` (igualdade por HMAC `DocumentHash`, nunca LIKE), `type` ou `personType` (PF|PJ), `riskLevel`
(LOW|MEDIUM|HIGH|CRITICAL), `pep` ou `isPep` (boolean), `sourceType` (ONBOARDED|MIGRATED), `page`, `size`, `sort`.
(O nome `riskCategory` desta spec foi substituído por `riskLevel`, campo real da entity/front.)
Roles: ANALYST, SUPERVISOR, ADMIN, AUDITOR. `GET /parties/search?q=` permanece (legado).

### GET /api/v1/parties/{id}
**Response 200** (`PartyResponse`, spec 046): `id, type, document*, name, tradeName, email*, phone*, motherName*, birthDate,
nationality, occupation, status, createdAt, updatedAt, partyRole, sourceType, relationshipStartDate/EndDate,
countryOfResidence, taxResidenceCountry, cboCode, declaredMonthlyIncome, declaredNetWorth, sourceOfFunds, sourceOfWealth,
purposeOfRelationship, expectedMonthlyVolume, cnaePrimary, cnaesSecondary, legalNatureCode, incorporationDate,
shareCapital, annualRevenue, companySize, nonprofit, publiclyTraded, uboIdentified, uboNotIdentifiedReason,
receitaStatus, receitaCheckedAt, kycLastUpdateAt, pep, pepStatus, pepCategory, pepRole, pepStartDate/EndDate/ExpiryDate,
pepApprovalAt, pepApprovalByName, riskLevel, riskScore, riskCalculatedAt, nextReviewDate, eddRequired,
assetFreezeActive/At/Reference`. (`*` = PII sempre mascarada.) `riskFactors` (fatores do score) **não** é devolvido:
ficam no `reason` do histórico de risco.

### POST /api/v1/parties | PUT /api/v1/parties/{id}
`PartyRequest`: `type, document, name, tradeName, email, phone, birthDate, nationality, occupation` + campos 046
opcionais (motherName, partyRole, countryOfResidence, cboCode, rendas, origem dos recursos, dados PJ...).
- `document` é obrigatório só na criação (400 se ausente) e **imutável**: no PUT é ignorado se ausente/mascarado
  (contém `*`) ou igual (mesmo formatado); diferente e completo → **422 `DOCUMENT_IMMUTABLE`**.
- Em PUT, `email`/`phone`/`motherName` com `*` ou `null` **mantêm** o valor atual; `""` limpa. Demais campos 046: só os não nulos são aplicados.
- Audita `PARTY_CREATED` / `PARTY_UPDATED` (somente nomes de campos, nunca valores de PII).

### POST /api/v1/parties/{id}/reveal  (ANALYST, SUPERVISOR, AUDITOR)
`{ "field": "document|email|phone|motherName" , "fields": [...], "reason": "10–500 chars" }` (`field` e/ou `fields`).
**Response 200**: `{ field, value, values:{campo:valor} }` só com os campos pedidos; `Cache-Control: no-store`.
Registra `data_access_log` (campos + `REVELACAO: <motivo>`, IP) e audita `PII_REVEALED` (sem valor). ADMIN não revela (403).

### POST /api/v1/parties/{id}/risk/recalculate  (ANALYST, SUPERVISOR)
Reaplica o `RiskScoringService` à parte (score, nível, próxima revisão, EDD, histórico, auditoria). Resposta: `PartyResponse`.
O recálculo global `POST /risk/recalculate` (ADMIN) permanece.

### GET /api/v1/parties/{id}/pep
`{ partyId, pep, pepStatus, category, role, startDate, endDate, expiryDate, approved, eddRequired, riskLevel, approvedAt, approvedByName }`.

### GET /api/v1/parties/{id}/beneficial-owners
Lista de DTO: `id, ownerDocument*, ownerName, ownershipPct, controlType, pep, finalBeneficiary, indirect (aliases
isFinalBeneficiary/isIndirect), nationality, validFrom/To, ownerType, ownerPartyId, ownerParty{id,name}, ownershipChain[{name,pct}]`.
`owner_document` é cifrado em repouso; `owner_document_hash` (HMAC) vincula automaticamente a parte cadastrada.
O `document_hash` da cadeia societária **não** é exposto.

### GET /api/v1/parties/{id}/relationships
`relatedParty` é resumo mascarado `{id, name, type, document*, pep, pepStatus, riskLevel}`; nunca a entity.

### GET /api/v1/kyc?partyId={id}[&status=]
Com `partyId`: todos os processos da parte (status opcional). Sem `partyId`: fila por `status` (padrão IN_PROGRESS).

### Transações (GET /transactions/{id}, /transactions/party/{partyId})
`direction` (IN|OUT), `cash` (alias `isCash`), `paymentMethod`, `counterpartyCountry`, `counterpartyBankIspb/Branch`,
`counterpartyAccount*`, `counterpartyDocument*` (cifrado em repouso + hash), `amountBrl`, `endToEndId`, `externalReference`.

### Screening
- `GET /api/v1/screening/lists` (ANALYST, SUPERVISOR, ADMIN, AUDITOR): catálogo real `{code, name, type, source, requiresAssetFreeze, active, lastUpdated}`.
- `POST /api/v1/screening/{id}/review` (ANALYST, SUPERVISOR): JSON `{ "status": "CONFIRMED|FALSE_POSITIVE", "justification": "≥ 20 não brancos" }`
  (também aceita `status`/`justification` em query/formulário, por compatibilidade). Justificativa curta → 422; status inválido → 400.
  Audita `SCREENING_REVIEWED`. CONFIRMED em lista CSNU bloqueia a parte (Lei 13.810).
- `ScreeningResultResponse` inclui `partyName, requiresAssetFreeze, reviewJustification, reviewedAt, reviewedByName`.

### GET /api/v1/parties/{id}/kyc-history
### GET /api/v1/parties/{id}/risk-history
### GET /api/v1/parties/{id}/screening
(Spec original; rotas reais em uso: `/kyc?partyId=`, `/risk-history/party/{id}`, `/screening/party/{id}`.)

---

## Relatórios

### GET /api/v1/reports
**Response 200**: lista de relatórios disponíveis.

### POST /api/v1/reports/{code}/generate
**Request**
```json
{
  "from": "2026-01-01",
  "to": "2026-10-31",
  "format": "PDF | EXCEL"
}
```
**Response 202**: `{ "jobId": "uuid" }`

### GET /api/v1/reports/jobs/{jobId}
**Response 200**: status + url de download quando pronto.

---

## Usuários

Todos os endpoints de usuário são **ADMIN**. O hash da senha nunca é devolvido.

### GET /api/v1/users (paginado) | GET /api/v1/users/{id}
### POST /api/v1/users
`{ username, email, password (10–128), name, roles[] }` → 201.

### PUT /api/v1/users/{id}
`{ name, email, roles[] }` (username imutável; e-mail duplicado → 422 `EMAIL_IN_USE`; perfil inexistente → 400;
ADMIN não pode remover o próprio perfil ADMIN → 422). Audita `USER_UPDATED`.

### POST /api/v1/users/{id}/deactivate | POST /api/v1/users/{id}/activate
Desativar revoga os refresh tokens. Audita `USER_DEACTIVATED` / `USER_ACTIVATED`.

### POST /api/v1/users/{id}/reset-password
Corpo opcional `{ "newPassword": "10–128 chars" }`.
**Response 200**: `{ userId, temporaryPassword, refreshTokensRevoked: true }`. Sem `newPassword`, o servidor gera senha
forte (16 caracteres, maiúscula+minúscula+dígito+símbolo) devolvida **uma única vez** (`Cache-Control: no-store`); com
`newPassword`, `temporaryPassword` vem nulo (a senha definida nunca é devolvida). Revoga todos os refresh tokens
(tokens de acesso já emitidos expiram em até 30 min). Audita `USER_PASSWORD_RESET` (sem a senha).
*Não há* flag "trocar senha no próximo login" (exigiria coluna nova; pendente).

---

## Auditoria e acesso a dados (AUDITOR, ADMIN, SUPERVISOR)

### GET /api/v1/audit
Filtros server-side, paginado (padrão `createdAt,desc`): `userId` ou `username`, `action`, `entityType`, `entityId`,
`from`, `to` (`yyyy-MM-dd` em America/Sao_Paulo — `to` até o fim do dia — ou ISO-8601). Período inválido → 400.
Resposta: `{ id, action, entityType, entityId, oldValue, newValue, user:{id,username,name}, ipAddress, hash, createdAt }`.
`oldValue`/`newValue` saem com PII **mascarada pelo nome da chave** (document/cpf/cnpj → últimos 4; email; phone;
motherName; password/secret/token → `***`; chaves `*Hash` preservadas). `GET /audit/entity/{tipo}/{id}` continua válido.

### GET /api/v1/data-access
Mesmos filtros (sem `action`). Resposta `{ id, user, entityType, entityId, fieldsAccessed, accessReason, ipAddress, createdAt }`.
`GET /data-access/entity/{tipo}/{id}` e `/data-access/user/{userId}` continuam válidos.

---

## Dispositivos e Push

Todos exigem autenticacao (`ANALYST`, `SUPERVISOR`, `ADMIN`, `AUDITOR`). O token do aparelho nunca e devolvido nem logado em claro.

- `POST /api/v1/devices` `{ token, platform: ANDROID|IOS|WEB, appVersion? }` -> **201** `{ id, platform, appVersion, createdAt, lastSeenAt }`.
  Upsert por token: vincula ao usuario autenticado e atualiza `lastSeenAt`; token de outro usuario e transferido (auditado `DEVICE_TOKEN_TRANSFERRED`).
- `DELETE /api/v1/devices/{token}` -> **204** (revoga; so o dono, senao 404).
- `GET|PUT /api/v1/devices/preferences` `{ criticalAlerts, deadlines }` (padrao `true/true`). `criticalAlerts` cobre alertas criticos e aprovacoes pendentes; `deadlines` cobre prazos regulatorios.

Push (spec 024): payload **somente** `data = { type: ALERT|CASE|SLA, id: <uuid> }` + titulo/corpo genericos fixos; nunca nome, documento, valor ou dado do cliente (tipping-off).
Provedor por `cactus.push.provider` = `noop` (padrao, so log debug) | `fcm` (FCM HTTP v1; `FCM_PROJECT_ID` + `FCM_CREDENTIALS_JSON` = caminho do JSON da conta de servico).
Gatilhos: alerta CRITICAL criado (responsavel, ou ANALYST+SUPERVISOR se nao atribuido); case em PENDING_APPROVAL (SUPERVISORes, exceto quem decidiu); job diario 08:00 BRT (`cactus.push.sla-cron`) de prazos (analise em ate 2 dias uteis, incluindo vencidos; comunicacao ate hoje) para responsavel + SUPERVISORes. Token `UNREGISTERED` e removido.

---

## Contrato de erro global

```json
{ "timestamp": "...", "status": 422, "error": "Unprocessable Content", "message": "...",
  "code": "BUSINESS_RULE", "traceId": "5f0c...", "fieldErrors": [{ "field": "x", "message": "..." }] }
```
`fieldErrors` só em validação de corpo. `code` estável: `VALIDATION_ERROR` (400), `UNAUTHORIZED` (401), `FORBIDDEN` (403),
`NOT_FOUND` (404), `METHOD_NOT_ALLOWED` (405), `BUSINESS_RULE` (422; pode ser especializado, ex.: `DOCUMENT_IMMUTABLE`,
`EMAIL_IN_USE`), `PAYLOAD_TOO_LARGE` (413), `RATE_LIMITED` (429), `INTERNAL_ERROR` (500).
`traceId` = header `X-Trace-Id` da resposta (propagado se o cliente enviar um id válido `[A-Za-z0-9._-]{8,64}`,
senão gerado) e MDC `traceId` nos logs. Nunca há stack trace nem valor rejeitado (pode ser PII).

---

## Observações
- Todos os endpoints de listagem retornam paginação padrão Spring.
- Busca textual (`q`) usa full-text search no backend.
- O frontend não deve assumir campos que não estejam documentados aqui.
- Novos endpoints devem seguir o mesmo padrão de resposta e erro.
