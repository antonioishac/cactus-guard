package br.com.cactus.guard.config;

import br.com.cactus.guard.config.ratelimit.LocalRateLimiter;
import br.com.cactus.guard.config.ratelimit.RateLimiter;
import br.com.cactus.guard.config.ratelimit.RedisRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Limitador de requisicoes. Backend {@code cactus.ratelimit.backend}: {@code redis} (padrao fora de teste; contadores
 * compartilhados entre instancias, com contingencia local se o Redis cair) ou {@code memory} (por instancia; perfil de
 * teste). Os limites por escopo ficam em {@link RateLimitFilter}/{@code cactus.ratelimit.*}.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public LocalRateLimiter localRateLimiter(@Value("${cactus.ratelimit.max-local-keys:200000}") int maxKeys) {
        return new LocalRateLimiter(maxKeys);
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public RateLimiter rateLimiter(@Value("${cactus.ratelimit.backend:redis}") String backend,
                                   LocalRateLimiter local, RedisConnectionFactory redisFactory,
                                   @Value("${cactus.ratelimit.redis-retry-after-failure:30s}") Duration retryAfterFailure) {
        if ("memory".equalsIgnoreCase(backend)) {
            return local;
        }
        return new RedisRateLimiter(new StringRedisTemplate(redisFactory), local, retryAfterFailure);
    }
}
