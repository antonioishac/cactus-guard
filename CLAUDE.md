# CLAUDE.md — CactusGuard PLD/FT

## Objetivo do Projeto

Plataforma SaaS de **Prevenção à Lavagem de Dinheiro e Financiamento do Terrorismo (PLD/FT)** para o mercado brasileiro.

**Nome**: CactusGuard  
**Pacote**: `br.com.cactus.guard`

## Status Atual (Backend)

| Métrica | Valor |
|---------|-------|
| Controllers | 30 |
| Services | 30 |
| Entities | 29 |
| Testes | 143 |
| Migrations | 20 |

## Módulos Implementados

- Auth + JWT + Roles
- Party CRUD + Full Text + Criptografia + Mascaramento + Document Hash + Campos PLD
- Beneficial Owner + Alerta PEP
- Endereços + Relacionamentos
- Risk History + Endpoint + Persistência automática
- Watchlist + Motor + Seed
- Transaction Profile + Endpoint + Campos de rastreabilidade
- Data Access Log (LGPD)
- Alertas + Cases (justificativa + SLA + impede reabertura)
- Motor de regras (7 tipos: PEP, Sanção, High Value, Velocity, Structuring, Unusual Pattern, Watchlist)
- Screening + Orchestrator + Async + Circuit Breaker
- SISCOAF + SAR + Orchestrator + Circuit Breaker
- KYC + Checklist validation + Upload real + Download + Circuit Breaker
- Relatórios PDF/Excel
- Migração com hash
- Audit + Data Retention Job
- Dashboard operacional
- Rate Limit + Métricas + Resilience4j
- Endpoint recalcular risco

## Stack

- Java 21 + Spring Boot 3.3
- PostgreSQL + Redis
- Resilience4j (circuit breaker)
- AES-256-GCM (criptografia)
- JWT + Spring Security

## Referências das Specs

Consulte sempre a pasta `specs/` antes de implementar.
