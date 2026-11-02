# Spec 045 — Dashboard

## Objetivo

Implementar a página Dashboard com métricas, gráficos e tabela de alertas recentes.

## Componentes

### MetricCard
- Ícone
- Label
- Valor (número grande)
- Variação (vs. ontem)

### Cards do Dashboard

| Card | Valor | Cor |
|------|-------|-----|
| Alertas Abertos | 47 | Vermelho |
| Casos Pendentes | 23 | Âmbar |
| Screenings | 1.284 | Teal |
| KYC Pendentes | 156 | Neutro |

### Gráficos

1. **Alertas ao Longo do Tempo** — Line chart (7 dias)
2. **Distribuição por Severidade** — Donut chart

### Tabela Alertas Recentes

| Coluna | Tipo |
|--------|------|
| ID do Alerta | Texto |
| Entidade | Texto |
| Tipo | Texto |
| Severidade | Badge |
| Data | Data |
| Status | Badge |

## API

```
GET /api/v1/dashboard/metrics
GET /api/v1/dashboard/alerts-chart
GET /api/v1/dashboard/severity-distribution
GET /api/v1/alerts?limit=5
```
