package br.com.cactus.guard.common.util;

import br.com.cactus.guard.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hash deterministico do documento (CPF/CNPJ): HMAC-SHA256 (hex, 64 chars) com pepper POR TENANT.
 * <p>
 * A chave de cada tenant e derivada por HKDF-SHA256 (RFC 5869) a partir do segredo mestre
 * {@code security.document-hash.key} (env DOCUMENT_HASH_KEY), com {@code salt = "cactusguard-document-hash-v2"} e
 * {@code info = "tenant:" + tenantId}. O mesmo CPF gera hashes diferentes em tenants diferentes, impedindo
 * correlacionar o titular entre clientes caso hashes vazem. {@link #of(String)} usa o tenant do contexto e
 * FALHA sem tenant (nunca cai em chave global). {@link #ofPlatform(String)} usa a chave mestre direta e serve so a
 * dados de plataforma que precisam de unicidade global (CNPJ do tenant).
 * <p>
 * O documento e normalizado (letras/digitos, maiusculas - cobre o CNPJ alfanumerico) antes do HMAC. Busca por
 * documento usa {@code document_hash}, pois a coluna cifrada tem IV aleatorio.
 * <p>
 * Fail-fast: o bean nao sobe sem chave de ao menos 32 caracteres. Trocar o segredo mestre exige
 * {@code POST /api/v1/admin/rehash-documents}.
 */
@Slf4j
@Component
public final class DocumentHash {

    private static final int MIN_KEY_LENGTH = 32;
    private static final byte[] HKDF_SALT = "cactusguard-document-hash-v2".getBytes(StandardCharsets.UTF_8);
    private static final Map<UUID, byte[]> TENANT_KEYS = new ConcurrentHashMap<>();
    private static volatile byte[] key;

    public DocumentHash(@Value("${security.document-hash.key:}") String configuredKey) {
        if (configuredKey == null || configuredKey.getBytes(StandardCharsets.UTF_8).length < MIN_KEY_LENGTH) {
            throw new IllegalStateException(
                    "security.document-hash.key (DOCUMENT_HASH_KEY) ausente ou menor que 32 caracteres");
        }
        key = configuredKey.getBytes(StandardCharsets.UTF_8);
        TENANT_KEYS.clear();
        log.info("Pepper de hash de documento carregado (derivacao por tenant)");
    }

    /** Hash do documento sob o pepper do tenant do contexto; sem tenant lanca {@link IllegalStateException}. */
    public static String of(String document) {
        return of(TenantContext.require(), document);
    }

    /** Hash do documento sob o pepper do tenant informado (rehash/jobs). */
    public static String of(UUID tenantId, String document) {
        if (tenantId == null || TenantContext.NO_TENANT.equals(tenantId)) {
            throw new IllegalStateException("Hash de documento exige um tenant");
        }
        return hmac(tenantKey(tenantId), document);
    }

    /** Hash com a chave mestre (sem tenant): apenas dados de plataforma com unicidade global. */
    public static String ofPlatform(String document) {
        return hmac(masterKey(), document);
    }

    private static byte[] masterKey() {
        byte[] k = key;
        if (k == null) {
            throw new IllegalStateException("DocumentHash nao inicializado");
        }
        return k;
    }

    private static byte[] tenantKey(UUID tenantId) {
        byte[] master = masterKey();
        return TENANT_KEYS.computeIfAbsent(tenantId, id -> hkdfSha256(master, HKDF_SALT,
                ("tenant:" + id).getBytes(StandardCharsets.UTF_8)));
    }

    private static String hmac(byte[] k, String document) {
        if (document == null) {
            throw new IllegalArgumentException("Documento nao pode ser nulo");
        }
        byte[] out = hmacSha256(k, normalize(document).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(out);
    }

    /** HKDF-SHA256 (RFC 5869) com saida de 32 bytes (um unico bloco de expand). */
    static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info) {
        byte[] prk = hmacSha256(salt, ikm);
        byte[] input = new byte[info.length + 1];
        System.arraycopy(info, 0, input, 0, info.length);
        input[info.length] = 0x01;
        return hmacSha256(prk, input);
    }

    private static byte[] hmacSha256(byte[] k, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(k, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponivel", e);
        }
    }

    static String normalize(String document) {
        return document.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
