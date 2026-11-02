# Spec 039 — Rate Limiting

## Objetivo

Proteger a API contra abuso e garantir disponibilidade para todos os clientes.

## Estratégia

Usar **Redis** como store de rate limit (compartilhado entre pods).

## Limites por endpoint

| Endpoint | Limite | Janela |
|----------|--------|--------|
| `POST /api/v1/auth/login` | 5 req | 1 min |
| `POST /api/v1/auth/refresh` | 10 req | 1 min |
| `GET /api/v1/parties/search` | 30 req | 1 min |
| `POST /api/v1/migration/**` | 10 req | 1 min |
| `POST /api/v1/reports/**/generate` | 5 req | 1 min |
| Demais endpoints autenticados | 100 req | 1 min |

## Implementação

Usar **Bucket4j** com Redis.

```java
@Bean
public RateLimiter rateLimiter(RedisTemplate<String, String> redis) {
    return Bucket4j.extension(Redis.class)
        .builder()
        .build();
}
```

## Resposta

Quando o limite for excedido:

```
HTTP 429 Too Many Requests
Retry-After: 60
{
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Limite de requisições excedido. Tente novamente em 60 segundos."
}
```

## Exceções

- Health check (`/api/v1/health`) não tem limite.
- Jobs internos (scheduler) não passam pelo rate limiter.

## Observações para manutenção

- Limites devem ser configuráveis via `application.yml`.
- Em ambiente de teste, desabilitar ou aumentar limites.
- Monitorar métrica `rate_limit.exceeded` para ajustar limites.
