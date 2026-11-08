package br.com.cactus.guard.device;

import br.com.cactus.guard.integration.push.PushMessage;
import br.com.cactus.guard.integration.push.PushSenderPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** O payload de push nunca carrega PII (tipping-off / tela bloqueada): so type+id e texto generico. */
class PushPayloadTest {

    private final UUID id = UUID.randomUUID();

    @Test
    void dataHasOnlyTypeAndId() {
        for (PushMessage m : List.of(PushMessage.criticalAlert(id), PushMessage.casePendingApproval(id),
                PushMessage.slaDeadline(id))) {
            assertEquals(PushMessage.ALLOWED_DATA_KEYS, m.data().keySet());
            assertEquals(id.toString(), m.data().get("id"));
            assertTrue(List.of("ALERT", "CASE", "SLA").contains(m.data().get("type")));
        }
    }

    @Test
    void textsAreGenericAndDoNotDependOnAnyInput() {
        PushMessage a = PushMessage.criticalAlert(UUID.randomUUID());
        PushMessage b = PushMessage.criticalAlert(UUID.randomUUID());
        assertEquals(a.title(), b.title());
        assertEquals(a.body(), b.body());
        assertEquals("Novo alerta crítico para análise", a.title());
        assertEquals("Prazo regulatório de case vencendo", PushMessage.slaDeadline(id).title());
    }

    @Test
    void maskNeverExposesFullToken() {
        String token = "abcdefghijklmnopqrstuvwxyz0123456789";
        String masked = PushSenderPort.mask(token);
        assertFalse(masked.contains("klmnopqrstuv"));
        assertEquals("***", PushSenderPort.mask("short"));
        assertEquals("***", PushSenderPort.mask(null));
    }

    @Test
    void dataMapIsImmutable() {
        Map<String, String> data = PushMessage.criticalAlert(id).data();
        assertThrows(UnsupportedOperationException.class, () -> data.put("name", "x"));
    }
}
