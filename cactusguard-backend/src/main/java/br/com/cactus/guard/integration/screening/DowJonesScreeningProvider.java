package br.com.cactus.guard.integration.screening;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.screening.entity.ScreeningResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "integrations.screening.provider", havingValue = "dowjones")
public class DowJonesScreeningProvider implements ScreeningProviderPort {

    private final RestClient restClient;

    public DowJonesScreeningProvider(
            @Value("${integrations.screening.base-url}") String baseUrl,
            @Value("${integrations.screening.api-key}") String apiKey,
            io.micrometer.observation.ObservationRegistry observationRegistry) {
        this.restClient = RestClient.builder()
                // span http.client.requests (tracing + metricas) para a chamada externa; propaga traceparent
                .observationRegistry(observationRegistry)
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    @CircuitBreaker(name = "screening", fallbackMethod = "screenFallback")
    @Retry(name = "screening")
    public ScreeningResult screen(Party party, List<String> listCodes) {
        log.info("Dow Jones screening for: {}", br.com.cactus.guard.common.util.DataMasker.maskDocument(party.getDocument()));

        Map<String, Object> request = Map.of(
                "document", party.getDocument(),
                "name", party.getName(),
                "lists", listCodes
        );

        Map response = restClient.post()
                .uri("/screening/v1/search")
                .body(request)
                .retrieve()
                .body(Map.class);

        boolean hasMatch = response != null && Boolean.TRUE.equals(response.get("match"));
        double score = response != null && response.get("score") != null 
                ? ((Number) response.get("score")).doubleValue() : 0.0;

        return ScreeningResult.builder()
                .party(party)
                .listCode(listCodes.isEmpty() ? "PEP" : listCodes.get(0))
                .matchScore(BigDecimal.valueOf(score))
                .status(hasMatch ? "PENDING" : "NO_MATCH")
                .build();
    }

    public ScreeningResult screenFallback(Party party, List<String> listCodes, Throwable t) {
        log.warn("Screening fallback triggered for party {}: {}", party.getId(), t.getMessage());
        return ScreeningResult.builder()
                .party(party)
                .listCode("FALLBACK")
                .matchScore(BigDecimal.ZERO)
                .status("ERROR")
                .build();
    }

    @Override
    public List<String> availableLists() {
        return List.of("PEP", "SANCTIONS", "ADVERSE_MEDIA", "OFAC");
    }
}
