package br.com.cactus.guard.rule.engine;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Leitura tipada dos parametros JSONB de uma regra, com fallback para o default do codigo. */
public final class RuleParams {

    private final Map<String, Object> map;

    public RuleParams(Map<String, Object> map) {
        this.map = map != null ? map : Map.of();
    }

    public BigDecimal decimal(String key, BigDecimal def) {
        Object v = map.get(key);
        if (v == null) return def;
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public int integer(String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v == null) return def;
        try {
            return new BigDecimal(v.toString()).intValue();
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean bool(String key, boolean def) {
        Object v = map.get(key);
        if (v instanceof Boolean b) return b;
        if (v == null) return def;
        return Boolean.parseBoolean(v.toString());
    }

    public boolean has(String key) {
        return map.get(key) != null;
    }

    public List<String> strings(String key, List<String> def) {
        Object v = map.get(key);
        if (v instanceof List<?> l) {
            return l.stream().map(String::valueOf).toList();
        }
        return def;
    }

    public Map<String, Object> asMap() {
        return map;
    }
}
