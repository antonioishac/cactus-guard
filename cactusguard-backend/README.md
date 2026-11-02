# CactusGuard Backend — v3.0

Plataforma PLD/FT — MVP Backend

## Stack
- Spring Boot 3.3.5 + Java 21
- PostgreSQL + Flyway
- Spring Security + JWT
- OpenPDF + Apache POI

## Como rodar
```bash
cd cactusguard-backend
mvn spring-boot:run
```

## Usuário inicial
- Username: `admin`
- Password: `admin123`

## Endpoints

### Autenticação
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`

### Party
- `POST /api/v1/parties`
- `GET /api/v1/parties`
- `GET /api/v1/parties/{id}`
- `PUT /api/v1/parties/{id}`
- `DELETE /api/v1/parties/{id}`

### Alertas
- `GET /api/v1/alerts`
- `GET /api/v1/alerts/{id}`
- `POST /api/v1/alerts/{id}/assume`
- `POST /api/v1/alerts/{id}/false-positive`

### Cases
- `POST /api/v1/cases/from-alert/{alertId}`
- `GET /api/v1/cases`
- `GET /api/v1/cases/{id}`
- `POST /api/v1/cases/{id}/decide`

### Regras
- `GET /api/v1/rules`
- `GET /api/v1/rules/category/{category}`
- `PATCH /api/v1/rules/{code}/toggle`
- `POST /api/v1/rules/engine/evaluate`

### Screening
- `GET /api/v1/screening/party/{partyId}`
- `GET /api/v1/screening`
- `POST /api/v1/screening/{id}/review`

### Relatórios
- `GET /api/v1/reports`
- `POST /api/v1/reports/{code}/generate`
- `GET /api/v1/reports/jobs`
- `GET /api/v1/reports/jobs/{jobId}`

### Transações
- `POST /api/v1/transactions`
- `GET /api/v1/transactions/{id}`
- `GET /api/v1/transactions/party/{partyId}`

### SISCOAF
- `POST /api/v1/siscoaf/draft`
- `POST /api/v1/siscoaf/{id}/submit`
- `GET /api/v1/siscoaf`
- `GET /api/v1/siscoaf/{id}`

### KYC
- `POST /api/v1/kyc/onboarding/{partyId}`
- `POST /api/v1/kyc/{id}/approve`
- `POST /api/v1/kyc/{id}/reject`
- `GET /api/v1/kyc`
- `GET /api/v1/kyc/{id}`

### Usuários (ADMIN)
- `POST /api/v1/users`
- `GET /api/v1/users`
- `GET /api/v1/users/{id}`
- `POST /api/v1/users/{id}/deactivate`

### Notificações
- `GET /api/v1/notifications`
- `GET /api/v1/notifications/unread-count`
- `POST /api/v1/notifications/{id}/read`

### Health
- `GET /api/v1/health`
- `GET /api/v1/health/stats`

## Testes
```bash
mvn test
```

Cobertura atual: Auth, Party, Alertas, Transações, Cases, KYC, Migração, Regras, Screening, Usuários, Notificações, SISCOAF, Relatórios, Health.

## Migrations
V1 a V13 (auth, party, alerts, cases, rules, screening, reports, transactions, siscoaf, kyc, notifications, migration, audit)
