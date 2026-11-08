package br.com.cactus.guard.integration.push;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Mensagem de push SEM PII (spec 024; Lei 9.613 art. 11, II - sem tipping-off; tela bloqueada).
 * Dados = somente {@code type} e {@code id}; titulo/corpo sao textos genericos fixos.
 * Nunca incluir nome, documento, valor ou qualquer dado do cliente.
 */
public record PushMessage(PushType type, UUID id, String title, String body) {

    public static final Set<String> ALLOWED_DATA_KEYS = Set.of("type", "id");

    public PushMessage {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
    }

    public static PushMessage criticalAlert(UUID alertId) {
        return new PushMessage(PushType.ALERT, alertId,
                "Novo alerta crítico para análise", "Abra o aplicativo para ver os detalhes.");
    }

    public static PushMessage casePendingApproval(UUID caseId) {
        return new PushMessage(PushType.CASE, caseId,
                "Case aguardando aprovação", "Abra o aplicativo para ver os detalhes.");
    }

    public static PushMessage slaDeadline(UUID caseId) {
        return new PushMessage(PushType.SLA, caseId,
                "Prazo regulatório de case vencendo", "Abra o aplicativo para ver os detalhes.");
    }

    /** Campos de dados enviados ao aparelho: exatamente {@link #ALLOWED_DATA_KEYS}. */
    public Map<String, String> data() {
        return Map.of("type", type.name(), "id", id.toString());
    }
}
