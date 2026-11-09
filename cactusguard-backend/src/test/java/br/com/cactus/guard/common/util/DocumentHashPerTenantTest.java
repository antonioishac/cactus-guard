package br.com.cactus.guard.common.util;

import br.com.cactus.guard.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DocumentHashPerTenantTest {

    @Test
    void sameCpfGeneratesDifferentHashesInDifferentTenants() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        String ha = TenantContext.callAs(a, () -> DocumentHash.of("123.456.789-09"));
        String hb = TenantContext.callAs(b, () -> DocumentHash.of("12345678909"));
        assertNotEquals(ha, hb);
        assertEquals(64, ha.length());
    }

    @Test
    void hashIsDeterministicAndNormalizedWithinTheTenant() {
        UUID a = UUID.randomUUID();
        String h1 = TenantContext.callAs(a, () -> DocumentHash.of("123.456.789-09"));
        String h2 = TenantContext.callAs(a, () -> DocumentHash.of("12345678909"));
        assertEquals(h1, h2);
        assertEquals(h1, DocumentHash.of(a, "12345678909"));
    }

    @Test
    void tenantHashDiffersFromPlatformHash() {
        assertNotEquals(DocumentHash.ofPlatform("12345678909"), DocumentHash.of(UUID.randomUUID(), "12345678909"));
    }

    @Test
    void withoutTenantFails() {
        assertThrows(IllegalStateException.class, () -> DocumentHash.of(TenantContext.NO_TENANT, "12345678909"));
        assertThrows(IllegalStateException.class, () -> DocumentHash.of(null, "12345678909"));
    }

    @Test
    void hkdfMatchesRfc5869TestCase1() {
        byte[] ikm = new byte[22];
        java.util.Arrays.fill(ikm, (byte) 0x0b);
        byte[] salt = HexFormat.of().parseHex("000102030405060708090a0b0c");
        byte[] info = HexFormat.of().parseHex("f0f1f2f3f4f5f6f7f8f9");
        // primeiros 32 bytes do OKM do caso de teste 1 do RFC 5869
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf",
                HexFormat.of().formatHex(DocumentHash.hkdfSha256(ikm, salt, info)));
    }
}
