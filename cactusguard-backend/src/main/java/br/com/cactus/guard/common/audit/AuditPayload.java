package br.com.cactus.guard.common.audit;

import br.com.cactus.guard.audit.service.AuditService;

import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Normaliza o conteudo de auditoria para tipos estaveis em JSON (String, Integer, Boolean, listas e
 * mapas). audit_log e append-only (trigger V22): se o mapa mudar de tipo no ciclo
 * serializar/desserializar (ex.: BigDecimal vira Double, Instant vira texto), o Hibernate considera a
 * entidade "suja" e emite um UPDATE que o banco rejeita. Valores nao inteiros viram String.
 */
public final class AuditPayload {

    private AuditPayload() {
    }

    public static void log(AuditService audit, String action, String entityType, UUID entityId,
                           Map<String, Object> oldValue, Map<String, Object> newValue) {
        audit.log(action, entityType, entityId, normalize(oldValue), normalize(newValue));
    }

    public static Map<String, Object> normalize(Map<String, Object> in) {
        if (in == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        in.forEach((k, v) -> out.put(k, value(v)));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object value(Object v) {
        if (v == null || v instanceof Boolean || v instanceof String || v instanceof Integer) {
            return v;
        }
        if (v instanceof BigDecimal b) {
            return b.toPlainString();
        }
        if (v instanceof Map<?, ?> m) {
            return normalize((Map<String, Object>) m);
        }
        if (v instanceof Iterable<?> it) {
            List<Object> list = new ArrayList<>();
            it.forEach(e -> list.add(value(e)));
            return list;
        }
        if (v instanceof Number || v instanceof TemporalAccessor || v instanceof UUID || v instanceof Enum<?>) {
            return v.toString();
        }
        return v.toString();
    }
}
