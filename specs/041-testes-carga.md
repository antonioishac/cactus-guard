# Spec 041 — Testes de Carga

## Objetivo

Garantir que o sistema suporte o volume esperado de operações sem degradação.

## Cenários

| Cenário | Volume | Duração | Critério de sucesso |
|---------|--------|---------|---------------------|
| Onboarding simultâneo | 100 req/s | 10 min | p99 < 500ms, erro < 1% |
| Avaliação de regras | 50 regras × 10k partes | 5 min | Completar sem timeout |
| Geração de relatórios | 20 jobs simultâneos | 5 min | Todos completarem |
| Busca full-text | 50 req/s | 10 min | p99 < 200ms |
| Migração em lote | 100k registros | 30 min | Taxa > 500 reg/s |

## Ferramenta

Usar **k6** ou **Gatling**.

### Exemplo k6

```javascript
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  stages: [
    { duration: '2m', target: 50 },
    { duration: '5m', target: 100 },
    { duration: '2m', target: 0 },
  ],
};

export default function () {
  const res = http.get('http://localhost:8080/api/v1/parties/search?q=test');
  check(res, { 'status is 200': (r) => r.status === 200 });
}
```

## Ambiente

- Banco com dados realistas (mínimo 100k partes, 1M transações).
- Redis ativo.
- Métricas coletadas durante o teste.

## Observações para manutenção

- Executar testes de carga antes de cada release major.
- Armazenar resultados para comparação histórica.
- Ajustar índices e queries com base nos resultados.

## Resultados — particionamento de `transactions` (2026-11-08)

Teste de volume local (container descartável `postgres:17-alpine`, `shared_buffers=512MB`, já removido): schema real das migrations V1–V44, **2.000.000 transações sintéticas** (sem PII; 2 tenants 80/20, 25 mil partes, ~330 dias, 3% em espécie), consultas executadas com o papel `cactus_app` (RLS ativo, `app.tenant_id` do tenant maior, 1,6 mi linhas) via `pgbench` (1 cliente; p95 sobre 150 execuções, 600 nas consultas por chave). "Depois" = após V48–V50 (mensal, 13 partições + DEFAULT). Migração dos 2 mi de linhas: ~1 min (copia + índices).

| Consulta (equivale a) | p95 antes | p95 depois | Obs. |
|---|---|---|---|
| Transações da parte, janela 90 d (`findByPartiesSince`/por parte) | 3,2 ms | 14,5 ms | regressão: ~5 partições × índice; segue baixo |
| Contagem por parte, janela 30 d, `HAVING` (`countByPartySinceHaving`) | 178 ms | 148 ms | |
| Volume por parte, janela 30 d (`volumeByParty`) | 401 ms | 322 ms | |
| Espécie ≥ R$ 50 mil sem COE, janela 7 d (`findCashWithoutCoe`) | 96 ms | 19 ms | índice parcial por partição |
| Valor ≥ limiar, janela 7 d (`findAtLeastSince`) | 147 ms | 103 ms | |
| Busca por `end_to_end_id` **sem data** (dedup/`findById`) | 2,0 ms | 8,6 ms | regressão: sonda TODAS as partições; piora linear com nº de partições |

Leitura: o ganho aparece nas varreduras por janela (poda de partições + índices parciais menores); o custo está nas consultas **sem filtro de data**. Mitigação recomendada: dedup/consumidor passarem a carregar uma janela de data (ex.: `end_to_end_id` + `transaction_date ± 1 d`; evento `TransactionReceived` levando `transactionDate`). Com 10 anos (120 partições) o custo de consulta sem data é ~10× o medido aqui. Números de uma única execução em máquina de desenvolvimento (Docker Desktop); servem para comparação relativa, não como SLA. Reexecutar antes de releases major.
