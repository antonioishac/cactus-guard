package br.com.cactus.guard.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Chaveiro AES-256 versionado (spec 040: rotacao a cada 90 dias).
 * <p>
 * Configuracao: {@code security.crypto.keys="v1:<base64-32bytes>,v2:<base64-32bytes>"} e
 * {@code security.crypto.active-version=v2}. Novos dados sao cifrados com a versao ativa e
 * prefixados com {@code v2:}; dados antigos continuam legiveis pela versao do prefixo. Dados
 * legados (sem prefixo) usam {@code security.crypto.legacy-key} (valor bruto da antiga
 * ENCRYPTION_KEY) ou, na ausencia, sao tentados com todas as chaves do chaveiro.
 * <p>
 * Fail-fast: sem chaves validas (32 bytes) a aplicacao nao sobe. Os perfis dev/test fornecem
 * chaves proprias em application-dev.yml / application-test.yml; producao deve injetar por env.
 * Nenhum valor de chave e logado.
 */
@Slf4j
@Component
public class EncryptionKeyring {

    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9_-]{1,16}");
    private static volatile EncryptionKeyring current;

    private final Map<String, byte[]> keys = new LinkedHashMap<>();
    private final String activeVersion;
    private final byte[] legacyKey;

    public EncryptionKeyring(
            @Value("${security.crypto.keys:}") String keysSpec,
            @Value("${security.crypto.active-version:}") String activeVersion,
            @Value("${security.crypto.legacy-key:}") String legacyKeyRaw) {
        if (keysSpec == null || keysSpec.isBlank()) {
            throw new IllegalStateException(
                    "security.crypto.keys (ENCRYPTION_KEYS) ausente: informe 'v1:<base64 de 32 bytes>[,v2:...]'");
        }
        for (String entry : keysSpec.split(",")) {
            String trimmed = entry.trim();
            int idx = trimmed.indexOf(':');
            if (idx <= 0) {
                throw new IllegalStateException("Entrada invalida em security.crypto.keys (esperado versao:base64)");
            }
            String version = trimmed.substring(0, idx);
            if (!VERSION.matcher(version).matches()) {
                throw new IllegalStateException("Versao de chave invalida em security.crypto.keys");
            }
            byte[] key;
            try {
                key = Base64.getDecoder().decode(trimmed.substring(idx + 1));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Chave da versao '" + version + "' nao e Base64 valido");
            }
            if (key.length != 32) {
                throw new IllegalStateException("Chave da versao '" + version + "' deve ter exatamente 32 bytes (AES-256)");
            }
            if (keys.put(version, key) != null) {
                throw new IllegalStateException("Versao de chave duplicada: " + version);
            }
        }
        this.activeVersion = (activeVersion == null || activeVersion.isBlank())
                ? keys.keySet().stream().reduce((a, b) -> b).orElseThrow()
                : activeVersion.trim();
        if (!keys.containsKey(this.activeVersion)) {
            throw new IllegalStateException("security.crypto.active-version '" + this.activeVersion + "' nao existe no chaveiro");
        }
        this.legacyKey = (legacyKeyRaw == null || legacyKeyRaw.isBlank()) ? null : deriveLegacy(legacyKeyRaw);
        current = this;
        log.info("Chaveiro de criptografia carregado: versoes={}, ativa={}, legacy={}",
                keys.keySet(), this.activeVersion, legacyKey != null);
    }

    /** Mesma derivacao historica: 16/24/32 bytes usados como estao; outro tamanho -> SHA-256. */
    private static byte[] deriveLegacy(String raw) {
        byte[] b = raw.getBytes(StandardCharsets.UTF_8);
        if (b.length == 16 || b.length == 24 || b.length == 32) {
            return b;
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(b);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }

    static EncryptionKeyring current() {
        EncryptionKeyring k = current;
        if (k == null) {
            throw new IllegalStateException("Chaveiro de criptografia nao inicializado");
        }
        return k;
    }

    String activeVersion() {
        return activeVersion;
    }

    byte[] activeKey() {
        return keys.get(activeVersion);
    }

    byte[] key(String version) {
        return keys.get(version);
    }

    /** Candidatas para dado sem prefixo: chave legada (se houver) e depois as do chaveiro. */
    List<byte[]> legacyCandidates() {
        List<byte[]> list = new ArrayList<>();
        if (legacyKey != null) {
            list.add(legacyKey);
        }
        list.addAll(keys.values());
        return list;
    }
}
