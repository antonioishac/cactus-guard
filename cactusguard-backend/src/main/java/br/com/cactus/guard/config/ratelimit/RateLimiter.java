package br.com.cactus.guard.config.ratelimit;

import java.time.Duration;

/** Contador de requisicoes por chave e janela. Implementacoes: Redis (distribuido) e memoria (por instancia). */
public interface RateLimiter {

    /** @param retryAfterSeconds quando negado, segundos ate a janela renovar (>= 1) */
    record Decision(boolean allowed, long retryAfterSeconds) {
        public static Decision permit() {
            return new Decision(true, 0);
        }

        public static Decision deny(long retryAfterSeconds) {
            return new Decision(false, Math.max(1, retryAfterSeconds));
        }
    }

    /** Consome 1 unidade da chave; negado quando o limite da janela ja foi atingido. */
    Decision tryAcquire(String key, int limit, Duration window);
}
