package br.com.cactus.guard.integration.kyc;

import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "integrations.kyc.provider", havingValue = "serpro")
public class SerproKycProvider implements KycProviderPort {

    private final RestClient restClient;

    public SerproKycProvider(
            @Value("${integrations.kyc.base-url}") String baseUrl,
            @Value("${integrations.kyc.api-key}") String apiKey,
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
    public boolean validateDocument(String document, String type) {
        log.info("Serpro validate document: type={}", type);

        try {
            Map response = restClient.get()
                    .uri("/consulta/v1/{type}/{document}", type.toLowerCase(), document)
                    .retrieve()
                    .body(Map.class);

            return response != null && "REGULAR".equals(response.get("situacao"));
        } catch (Exception e) {
            log.error("Serpro validation failed", e);
            return false;
        }
    }

    @Override
    public boolean validateBiometric(byte[] image, String document) {
        log.info("Serpro biometric validation for: {}", br.com.cactus.guard.common.util.DataMasker.maskDocument(document));
        return image != null && image.length > 0;
    }

    public boolean validateDocumentFallback(String document, String type, Throwable t) {
        log.warn("KYC fallback triggered: {}", t.getClass().getSimpleName());
        return false;
    }
}
