package br.com.cactus.guard.security;

import br.com.cactus.guard.common.util.DocumentHash;
import br.com.cactus.guard.crypto.EncryptedStringConverter;
import br.com.cactus.guard.crypto.EncryptionKeyring;
import br.com.cactus.guard.job.DataRetentionJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CryptoAndHashTest {

    private static final String K1 = "dGVzdC1vbmx5LWVuY3J5cHRpb24ta2V5LTMyYnl0ZSE=";
    private static final String K2 = "dGVzdC1vbmx5LWVuY3J5cHRpb24ta2V5LTMyYnl0ZUU=";

    @Autowired
    private DataRetentionJob retentionJob;

    @AfterEach
    void restoreKeyring() {
        // o chaveiro e estatico (compartilhado com os demais testes do mesmo JVM)
        new EncryptionKeyring("v1:" + K1, "v1", "");
    }

    @Test
    void ciphertextCarriesVersionPrefixAndRoundTrips() {
        var c = new EncryptedStringConverter();
        String enc = c.convertToDatabaseColumn("12345678901");
        assertTrue(enc.startsWith("v1:"));
        assertFalse(enc.contains("12345678901"));
        assertEquals("12345678901", c.convertToEntityAttribute(enc));
        assertNotEquals(enc, c.convertToDatabaseColumn("12345678901")); // IV aleatorio
    }

    @Test
    void rotationKeepsReadingOldVersionAndWritesNewOne() {
        var c = new EncryptedStringConverter();
        String old = c.convertToDatabaseColumn("segredo");
        new EncryptionKeyring("v1:" + K1 + ",v2:" + K2, "v2", "");
        String fresh = c.convertToDatabaseColumn("segredo");
        assertTrue(fresh.startsWith("v2:"));
        assertEquals("segredo", c.convertToEntityAttribute(old));
        assertEquals("segredo", c.convertToEntityAttribute(fresh));
    }

    @Test
    void legacyUnprefixedCiphertextIsStillReadable() throws Exception {
        byte[] key = Base64.getDecoder().decode(K1);
        byte[] iv = new byte[12];
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] enc = cipher.doFinal("legado".getBytes());
        byte[] all = new byte[12 + enc.length];
        System.arraycopy(iv, 0, all, 0, 12);
        System.arraycopy(enc, 0, all, 12, enc.length);
        assertEquals("legado", new EncryptedStringConverter().convertToEntityAttribute(Base64.getEncoder().encodeToString(all)));
    }

    @Test
    void keyringFailsFastWithoutOrWithShortKey() {
        assertThrows(IllegalStateException.class, () -> new EncryptionKeyring("", "", ""));
        assertThrows(IllegalStateException.class,
                () -> new EncryptionKeyring("v1:" + Base64.getEncoder().encodeToString(new byte[16]), "v1", ""));
        assertThrows(IllegalStateException.class, () -> new EncryptionKeyring("v1:" + K1, "v9", ""));
    }

    @Test
    void documentHashIsHmacNormalizedAndKeyed() {
        String a = DocumentHash.of("123.456.789-01");
        assertEquals(a, DocumentHash.of("12345678901"));
        assertEquals(64, a.length());
        assertEquals(DocumentHash.of("ab12cd34ef56"), DocumentHash.of("AB12CD34EF56"));
        assertThrows(IllegalStateException.class, () -> new DocumentHash("curta"));
    }

    @Test
    void retentionHasTenYearFloor() {
        assertEquals(3653, DataRetentionJob.MIN_RETENTION_DAYS);
        assertTrue(retentionJob.getRetentionDays() >= 3653);
        assertThrows(IllegalStateException.class, () -> new DataRetentionJob(null, null, 1825));
        assertDoesNotThrow(() -> new DataRetentionJob(null, null, 3653));
    }
}
