package br.com.cactus.guard.integration.siscoaf;

import org.springframework.web.client.RestClient;

import br.com.cactus.guard.siscoaf.entity.SiscoafCommunication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "integrations.siscoaf.provider", havingValue = "coaf")
public class CoafSiscoafProvider implements SiscoafProviderPort {

    private final RestClient restClient;

    public CoafSiscoafProvider(
            @Value("${integrations.siscoaf.base-url}") String baseUrl,
            @Value("${integrations.siscoaf.certificate-path:}") String certPath,
            io.micrometer.observation.ObservationRegistry observationRegistry) {
        this.restClient = RestClient.builder()
                // span http.client.requests (tracing + metricas) para a chamada externa; propaga traceparent
                .observationRegistry(observationRegistry)
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/xml")
                .build();
        log.info("COAF provider initialized. Certificate: {}", certPath.isEmpty() ? "none" : certPath);
    }

    @Override
    public String submit(SiscoafCommunication communication) {
        log.info("Submitting to COAF: type={}, case={}", 
                communication.getType(), 
                communication.getCaseEntity() != null ? communication.getCaseEntity().getId() : "N/A");

        String xml = buildXml(communication);

        try {
            Map response = restClient.post()
                    .uri("/comunicacao/v1/enviar")
                    .body(xml)
                    .retrieve()
                    .body(Map.class);

            return response != null ? (String) response.get("protocolo") : null;
        } catch (Exception e) {
            log.error("COAF submission failed", e);
            throw new RuntimeException("SISCOAF provider unavailable", e);
        }
    }

    @Override
    public String checkStatus(String protocolNumber) {
        try {
            Map response = restClient.get()
                    .uri("/comunicacao/v1/status/{protocolo}", protocolNumber)
                    .retrieve()
                    .body(Map.class);

            return response != null ? (String) response.get("status") : "UNKNOWN";
        } catch (Exception e) {
            log.error("COAF status check failed", e);
            return "ERROR";
        }
    }

    private String buildXml(SiscoafCommunication comm) {
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <Comunicacao>
                <Tipo>%s</Tipo>
                <CaseId>%s</CaseId>
                <Payload>%s</Payload>
            </Comunicacao>
            """,
                comm.getType(),
                comm.getCaseEntity() != null ? comm.getCaseEntity().getId() : "",
                comm.getPayload() != null ? comm.getPayload().toString() : ""
        );
    }

    public String submitFallback(SiscoafCommunication communication, Throwable t) {
        log.warn("SISCOAF fallback triggered: {}", t.getMessage());
        return "FALLBACK-" + System.currentTimeMillis();
    }
}
