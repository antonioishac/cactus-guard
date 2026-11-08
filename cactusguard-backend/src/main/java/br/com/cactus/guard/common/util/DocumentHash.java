package br.com.cactus.guard.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Hash deterministico do documento (CPF/CNPJ): HMAC-SHA256 (hex, 64 chars) com segredo (pepper)
 * {@code security.document-hash.key} (env DOCUMENT_HASH_KEY).
 * <p>
 * SHA-256 puro de CPF e reversivel por forca bruta (~10^9 combinacoes); com o pepper fora do banco
 * o hash vazado deixa de ser invertivel. O documento e normalizado (somente letras/digitos,
 * maiusculas - cobre o CNPJ alfanumerico) antes do HMAC. A coluna {@code document} e cifrada com IV
 * aleatorio, portanto buscas por documento usam {@code document_hash}.
 * <p>
 * Fail-fast: o bean nao sobe sem chave de ao menos 32 caracteres. Trocar o pepper exige
 * {@code POST /api/v1/admin/rehash-documents}.
 */
@Slf4j
@Component
public final class DocumentHash {

    private static final int MIN_KEY_LENGTH = 32;
    private static volatile byte[] key;

    public DocumentHash(@Value("${security.document-hash.key:}") String configuredKey) {
        if (configuredKey == null || configuredKey.getBytes(StandardCharsets.UTF_8).length < MIN_KEY_LENGTH) {
            throw new IllegalStateException(
                    "security.document-hash.key (DOCUMENT_HASH_KEY) ausente ou menor que 32 caracteres");
        }
        key = configuredKey.getBytes(StandardCharsets.UTF_8);
        log.info("Pepper de hash de documento carregado");
    }

    public static String of(String document) {
        if (document == null) {
            throw new IllegalArgumentException("Documento nao pode ser nulo");
        }
        byte[] k = key;
        if (k == null) {
            throw new IllegalStateException("DocumentHash nao inicializado");
        }
        String normalized = normalize(document);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(k, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponivel", e);
        }
    }

    static String normalize(String document) {
        return document.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
