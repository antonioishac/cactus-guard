package br.com.cactus.guard.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AES-256-GCM. Formato gravado: {@code <versao>:<base64(iv || ciphertext+tag)>} (ex.: {@code v1:...}).
 * Dados legados sem prefixo continuam legiveis (ver {@link EncryptionKeyring}) e sao regravados
 * com prefixo na proxima escrita. Nunca registra valores em log.
 */
@Slf4j
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final Pattern PREFIX = Pattern.compile("^([A-Za-z0-9_-]{1,16}):(.*)$", Pattern.DOTALL);
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        EncryptionKeyring ring = EncryptionKeyring.current();
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(ring.activeKey(), "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return ring.activeVersion() + ":" + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            log.error("Falha ao cifrar atributo");
            throw new IllegalStateException("Encryption failed");
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        EncryptionKeyring ring = EncryptionKeyring.current();
        try {
            Matcher m = PREFIX.matcher(dbData);
            if (m.matches() && ring.key(m.group(1)) != null) {
                return decrypt(ring.key(m.group(1)), Base64.getDecoder().decode(m.group(2)));
            }
            byte[] raw = Base64.getDecoder().decode(dbData);
            for (byte[] candidate : ring.legacyCandidates()) {
                try {
                    return decrypt(candidate, raw);
                } catch (GeneralSecurityException ignored) {
                    // tenta a proxima chave
                }
            }
            throw new GeneralSecurityException("no key");
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.error("Falha ao decifrar atributo (chave/versao desconhecida ou dado corrompido)");
            throw new IllegalStateException("Decryption failed");
        }
    }

    private static String decrypt(byte[] key, byte[] combined) throws GeneralSecurityException {
        if (combined.length <= GCM_IV_LENGTH) {
            throw new GeneralSecurityException("short");
        }
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(GCM_TAG_LENGTH, combined, 0, GCM_IV_LENGTH));
        return new String(cipher.doFinal(combined, GCM_IV_LENGTH, combined.length - GCM_IV_LENGTH), StandardCharsets.UTF_8);
    }
}
