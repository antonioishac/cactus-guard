package br.com.cactus.guard.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class RateLimitConfig {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Bean
    public Map<String, Bucket> rateLimitBuckets() {
        buckets.put("login", createBucket(5, Duration.ofMinutes(1)));
        buckets.put("search", createBucket(30, Duration.ofMinutes(1)));
        buckets.put("default", createBucket(100, Duration.ofMinutes(1)));
        return buckets;
    }

    private Bucket createBucket(int capacity, Duration duration) {
        return Bucket.builder()
                .addLimit(Bandwidth.simple(capacity, duration))
                .build();
    }
}
