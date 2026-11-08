package br.com.cactus.guard.common.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mascara PII em estruturas livres (old/new de auditoria) pelo NOME da chave. Chaves terminadas em
 * "hash" (HMAC) nao sao PII e sao preservadas. Segredos viram "***".
 */
public final class PiiMasker {

    private PiiMasker() {}

    public static Map<String, Object> maskMap(Map<String, Object> in) {
        if (in == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        in.forEach((k, v) -> out.put(k, maskValue(k, v)));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object maskValue(String key, Object v) {
        if (v instanceof Map<?, ?> m) return maskMap((Map<String, Object>) m);
        if (v instanceof Iterable<?> it) {
            List<Object> list = new ArrayList<>();
            it.forEach(e -> list.add(e instanceof Map<?, ?> m ? maskMap((Map<String, Object>) m) : maskScalar(key, e)));
            return list;
        }
        return maskScalar(key, v);
    }

    private static Object maskScalar(String key, Object v) {
        if (!(v instanceof String s) || key == null) return v;
        String k = key.toLowerCase(Locale.ROOT).replace("_", "");
        if (k.endsWith("hash")) return v;
        if (k.contains("password") || k.contains("secret") || k.contains("token")) return "***";
        if (k.contains("mother")) return DataMasker.maskName(s);
        if (k.contains("email")) return DataMasker.maskEmail(s);
        if (k.contains("phone")) return DataMasker.maskPhone(s);
        if (k.contains("document") || k.contains("cpf") || k.contains("cnpj")) return DataMasker.maskDocument(s);
        return v;
    }
}
