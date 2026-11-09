package br.com.cactus.guard.config.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

/**
 * Limitador DISTRIBUIDO (todas as instancias compartilham o contador): janela fixa em Redis, atomica via Lua
 * (INCR + PEXPIRE no primeiro hit; devolve contagem e PTTL). Janela fixa admite rajada de ate 2x o limite na virada
 * da janela - aceitavel para protecao de abuso (o bloqueio progressivo do login cobre forca bruta).
 * <p>
 * Redis indisponivel: cai para o {@link LocalRateLimiter} (por instancia) e so volta a tentar o Redis apos
 * {@code retryAfterFailure}, evitando acrescentar o timeout de conexao a cada requisicao. Nunca bloqueia todos os
 * usuarios por falha de infraestrutura (fail-open para o limite por instancia, nao para "sem limite").
 */
@Slf4j
public class RedisRateLimiter implements RateLimiter {

    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>("""
            local c = redis.call('INCR', KEYS[1])
            if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); ttl = tonumber(ARGV[1]) end
            return {c, ttl}
            """, List.class);

    private final StringRedisTemplate redis;
    private final LocalRateLimiter fallback;
    private final long retryAfterFailureNanos;
    private volatile long degradedUntilNanos;

    public RedisRateLimiter(StringRedisTemplate redis, LocalRateLimiter fallback, Duration retryAfterFailure) {
        this.redis = redis;
        this.fallback = fallback;
        this.retryAfterFailureNanos = retryAfterFailure.toNanos();
    }

    @Override
    public Decision tryAcquire(String key, int limit, Duration window) {
        long now = System.nanoTime();
        if (now - degradedUntilNanos < 0) {
            return fallback.tryAcquire(key, limit, window);
        }
        try {
            List<?> r = redis.execute(SCRIPT, List.of("rl:" + key), String.valueOf(window.toMillis()));
            if (r == null || r.size() < 2) {
                throw new IllegalStateException("resposta invalida do script de rate limit");
            }
            long count = ((Number) r.get(0)).longValue();
            long ttlMs = ((Number) r.get(1)).longValue();
            return count <= limit ? Decision.permit() : Decision.deny((ttlMs + 999) / 1000);
        } catch (RuntimeException e) {
            degradedUntilNanos = now + retryAfterFailureNanos;
            log.warn("Redis indisponivel para o rate limit; usando limitador local por instancia ({})",
                    e.getClass().getSimpleName());
            return fallback.tryAcquire(key, limit, window);
        }
    }
}
