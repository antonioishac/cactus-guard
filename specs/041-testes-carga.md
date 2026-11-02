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
