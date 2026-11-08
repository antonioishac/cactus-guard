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

### GET /api/v1/alerts
**Query params**: `status`, `severity`, `type`, `assignedTo`, `from`, `to`, `partyId`, `page`, `size`  
**Response 200**
```json
{
  "content": [
    {
      "id": "uuid",
      "type": "TRANSACTIONAL",
      "severity": "HIGH",
      "status": "OPEN",
      "partyName": "string",
      "partyDocument": "string",
      "triggeredAt": "2026-10-31T10:00:00Z",
      "dueDate": "2026-11-05T10:00:00Z",
      "assignedTo": "uuid"
    }
  ],
  "totalElements": 150,
  "totalPages": 8
}
```

### GET /api/v1/alerts/{id}
**Response 200**: detalhe completo (inclui regra, contexto, histórico resumido).

### POST /api/v1/alerts/{id}/assume
### POST /api/v1/alerts/{id}/escalate
### POST /api/v1/alerts/{id}/false-positive
**Response 200**: alerta atualizado.

---

## Cases

### GET /api/v1/cases
**Query params**: `status`, `assignedTo`, `slaStatus`, `riskCategory`, `page`, `size`

### GET /api/v1/cases/{id}
**Response 200**: case + dossiê resumido.

### POST /api/v1/cases
**Request**
```json
{
  "alertId": "uuid",
  "justification": "string"
}
```

### POST /api/v1/cases/{id}/decide
Decisão final regulatória (specs 003, 008, 012). Escalar **não** é decisão.
**Request**
```json
{
  "decision": "COMMUNICATE | ARCHIVE",
  "justification": "string (obrigatória, não vazia)"
}
```
- Case sem exigência de quatro olhos → `CLOSED`. Se `COMMUNICATE`, cria comunicação SISCOAF com prazo D+1 útil.
- Case com PEP / alto risco / EDD → `PENDING_APPROVAL` (decisão proposta, aguarda aprovação).
- **422**: justificativa ausente; case já encerrado (não reabre).

### POST /api/v1/cases/{id}/escalate
Transição de workflow para nível superior (não encerra o case).
```json
{ "reason": "string (obrigatória)", "targetRole": "SUPERVISOR | COMPLIANCE_OFFICER" }
```

### POST /api/v1/cases/{id}/approve
Aprovação de nível superior (quatro olhos). Permissão `case:approve`.
```json
{ "approved": true, "justification": "string (obrigatória)" }
```
- **403** se o aprovador for o mesmo usuário que propôs a decisão.
- `approved=false` → case volta a `IN_ANALYSIS` com a justificativa registrada.

---

## Partes (KYC / PEP)

### GET /api/v1/parties
**Query params**: `q` (full-text), `document`, `riskCategory`, `isPep`, `sourceType`, `page`, `size`

### GET /api/v1/parties/{id}
**Response 200**: ficha completa (KYC atual, PEP status, risco atual, UBO).

### GET /api/v1/parties/{id}/kyc-history
### GET /api/v1/parties/{id}/risk-history
### GET /api/v1/parties/{id}/screening

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

### GET /api/v1/users
### POST /api/v1/users
### PUT /api/v1/users/{id}
### POST /api/v1/users/{id}/deactivate

---

## Observações
- Todos os endpoints de listagem retornam paginação padrão Spring.
- Busca textual (`q`) usa full-text search no backend.
- O frontend não deve assumir campos que não estejam documentados aqui.
- Novos endpoints devem seguir o mesmo padrão de resposta e erro.
