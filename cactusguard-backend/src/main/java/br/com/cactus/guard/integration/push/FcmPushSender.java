package br.com.cactus.guard.integration.push;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Envio via FCM HTTP v1 (sem SDK). Ativado com {@code cactus.push.provider=fcm};
 * exige FCM_PROJECT_ID e FCM_CREDENTIALS_JSON (caminho do JSON da conta de servico).
 * Falha transitoria (5xx/429/rede) -> retry + circuit breaker; UNREGISTERED/404 -> {@link PushResult#INVALID_TOKEN}
 * sem retry. Payload: somente type+id e texto generico (ver {@link PushMessage}).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "cactus.push.provider", havingValue = "fcm")
public class FcmPushSender implements PushSenderPort {

    private final RestClient restClient;
    private final Supplier<String> accessToken;
    private final String projectId;

    @Autowired
    public FcmPushSender(@Value("${cactus.push.fcm.project-id:}") String projectId,
                         @Value("${cactus.push.fcm.credentials-path:}") String credentialsPath,
                         @Value("${cactus.push.fcm.base-url:https://fcm.googleapis.com}") String baseUrl) {
        this(projectId, RestClient.builder().baseUrl(baseUrl).build(),
                new FcmAccessTokenProvider(requireCredentials(projectId, credentialsPath)));
    }

    /** Para testes. */
    public FcmPushSender(String projectId, RestClient restClient, Supplier<String> accessToken) {
        this.projectId = projectId;
        this.restClient = restClient;
        this.accessToken = accessToken;
    }

    private static String requireCredentials(String projectId, String path) {
        if (projectId == null || projectId.isBlank() || path == null || path.isBlank()) {
            throw new IllegalStateException(
                    "cactus.push.provider=fcm exige FCM_PROJECT_ID e FCM_CREDENTIALS_JSON (caminho do JSON)");
        }
        return path;
    }

    @Override
    @CircuitBreaker(name = "push-fcm", fallbackMethod = "sendFallback")
    @Retry(name = "push-fcm")
    public PushResult send(String deviceToken, PushMessage message) {
        PushResult result = restClient.post()
                .uri("/v1/projects/{p}/messages:send", projectId)
                .header("Authorization", "Bearer " + accessToken.get())
                .contentType(MediaType.APPLICATION_JSON)
                .body(buildRequestBody(deviceToken, message))
                .exchange((req, res) -> {
                    HttpStatusCode status = res.getStatusCode();
                    if (status.is2xxSuccessful()) {
                        return PushResult.SENT;
                    }
                    String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    if (status.value() == 404 || body.contains("UNREGISTERED")) {
                        return PushResult.INVALID_TOKEN;
                    }
                    if (status.is4xxClientError() && status.value() != 429) {
                        log.warn("FCM rejeitou push: status={} type={}", status.value(), message.type());
                        return PushResult.FAILED;
                    }
                    throw new IllegalStateException("FCM indisponivel: status=" + status.value());
                });
        if (result == PushResult.INVALID_TOKEN) {
            log.info("FCM: token invalido (UNREGISTERED) {}", PushSenderPort.mask(deviceToken));
        }
        return result;
    }

    PushResult sendFallback(String deviceToken, PushMessage message, Throwable t) {
        log.warn("Push FCM em fallback: {} token={}", t.getClass().getSimpleName(), PushSenderPort.mask(deviceToken));
        return PushResult.FAILED;
    }

    /** Corpo HTTP v1. Dados limitados a {@link PushMessage#data()} (type, id). */
    static Map<String, Object> buildRequestBody(String deviceToken, PushMessage m) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("token", deviceToken);
        msg.put("notification", Map.of("title", m.title(), "body", m.body()));
        msg.put("data", m.data());
        msg.put("android", Map.of("priority", "HIGH"));
        msg.put("apns", Map.of("headers", Map.of("apns-priority", "10")));
        return Map.of("message", msg);
    }
}
