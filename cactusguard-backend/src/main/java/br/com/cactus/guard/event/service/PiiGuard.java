package br.com.cactus.guard.event.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Garante que o {@code data} de um evento nao carrega PII (LGPD / spec 040): rejeita, recursivamente, chaves cujo
 * nome indique documento, e-mail, telefone, nome, endereco ou nascimento. Os eventos carregam so ids e campos nao
 * pessoais; o consumidor busca o resto no banco.
 */
public final class PiiGuard {

    /** Fragmentos proibidos em nomes de campo (comparacao em minusculas, sem separadores). */
    private static final Set<String> FORBIDDEN_FRAGMENTS = Set.of(
            "document", "cpf", "cnpj", "email", "phone", "telefone", "celular", "name", "nome",
            "address", "endereco", "birth", "nascimento", "passport", "rg", "mother");

    /** Campos que contem um fragmento proibido mas sao inofensivos (ids tecnicos). */
    private static final Set<String> ALLOWED = Set.of("rulename");

    private PiiGuard() {
    }

    /** @throws IllegalArgumentException se alguma chave sugerir PII. */
    public static void assertNoPii(Map<String, ?> data) {
        check(data, "data");
    }

    @SuppressWarnings("unchecked")
    private static void check(Object value, String path) {
        if (value instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String key = String.valueOf(e.getKey());
                String norm = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
                if (!ALLOWED.contains(norm) && isForbidden(norm)) {
                    throw new IllegalArgumentException("Evento com campo potencialmente PII: " + path + "." + key);
                }
                check(e.getValue(), path + "." + key);
            }
        } else if (value instanceof List<?> l) {
            for (Object o : l) {
                check(o, path + "[]");
            }
        }
    }

    private static boolean isForbidden(String normalizedKey) {
        for (String f : FORBIDDEN_FRAGMENTS) {
            if (f.length() <= 2) {
                if (normalizedKey.equals(f)) {
                    return true;
                }
            } else if (normalizedKey.contains(f)) {
                return true;
            }
        }
        return false;
    }
}
