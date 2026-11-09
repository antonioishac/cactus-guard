package br.com.cactus.guard.config.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador em memoria (bucket4j, token bucket greedy) POR INSTANCIA. Usado no perfil de teste, quando
 * {@code cactus.ratelimit.backend=memory} e como contingencia quando o Redis esta indisponivel. Tabela limitada
 * ({@code maxKeys}) e limpa periodicamente: um atacante girando IPs nao consegue estourar a memoria.
 */
public class LocalRateLimiter implements RateLimiter {

    private record Entry(Bucket bucket, long windowNanos, long[] lastAccess) {
    }

    private final ConcurrentHashMap<String, Entry> buckets = new ConcurrentHashMap<>();
    private final int maxKeys;

    public LocalRateLimiter(int maxKeys) {
        this.maxKeys = maxKeys;
    }

    @Override
    public Decision tryAcquire(String key, int limit, Duration window) {
        if (buckets.size() >= maxKeys && !buckets.containsKey(key)) {
            evictIdle();
            if (buckets.size() >= maxKeys) {
                // tabela cheia de chaves ativas: abre espaco removendo uma qualquer (preferivel a crescer sem limite)
                buckets.keySet().stream().findFirst().ifPresent(buckets::remove);
            }
        }
        Entry e = buckets.computeIfAbsent(key + "|" + limit + "|" + window.toSeconds(), k -> new Entry(
                Bucket.builder().addLimit(Bandwidth.builder().capacity(limit).refillGreedy(limit, window).build()).build(),
                window.toNanos(), new long[]{System.nanoTime()}));
        e.lastAccess()[0] = System.nanoTime();
        ConsumptionProbe probe = e.bucket().tryConsumeAndReturnRemaining(1);
        return probe.isConsumed() ? Decision.permit()
                : Decision.deny((probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
    }

    @Scheduled(fixedDelay = 60_000)
    public void evictIdle() {
        long now = System.nanoTime();
        buckets.entrySet().removeIf(en -> now - en.getValue().lastAccess()[0] > 2 * en.getValue().windowNanos());
    }

    int size() {
        return buckets.size();
    }
}
