package br.com.cactus.guard.audit;

import br.com.cactus.guard.audit.dto.AuditIntegrityResponse;
import br.com.cactus.guard.audit.service.AuditChainJob;
import br.com.cactus.guard.audit.service.AuditIntegrityService;
import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.securityevent.entity.SecurityEvent;
import br.com.cactus.guard.securityevent.repository.SecurityEventRepository;
import br.com.cactus.guard.support.AbstractPostgresIT;
import br.com.cactus.guard.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cadeia de hash do audit_log (V45) em PostgreSQL real: inserts concorrentes mantem a cadeia linear por tenant e
 * qualquer adulteracao (feita como superusuario, com o trigger de protecao desligado) e detectada.
 */
class AuditChainIT extends AbstractPostgresIT {

    @Autowired
    private AuditService auditService;
    @Autowired
    private AuditIntegrityService integrity;
    @Autowired
    private AuditChainJob job;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SecurityEventRepository securityEvents;

    private void audit(String action) {
        tx.executeWithoutResult(s -> auditService.log(action, "TEST", UUID.randomUUID(), Map.of("a", 1), Map.of("b", "x")));
    }

    private void admin(String... sql) throws Exception {
        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement st = c.createStatement()) {
            for (String q : sql) {
                st.execute(q);
            }
        }
    }

    @Test
    void concurrentInsertsKeepTheChainLinearAndVerifiable() throws Exception {
        long before = integrity.verify().verifiedRecords();
        int threads = 8;
        int perThread = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> fs = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            fs.add(pool.submit(() -> {
                TenantContext.set(TenantContext.DEFAULT_TENANT_ID);
                try {
                    for (int i = 0; i < perThread; i++) {
                        audit("CONCURRENT");
                    }
                } finally {
                    TenantContext.clear();
                }
            }));
        }
        for (Future<?> f : fs) {
            f.get();
        }
        pool.shutdown();

        AuditIntegrityResponse r = integrity.verify();
        assertTrue(r.ok(), () -> "cadeia deve estar integra: " + r.firstDivergence());
        assertEquals(before + (long) threads * perThread, r.verifiedRecords());
        // sem bifurcacao: sequencias 1..N sem buracos nem repeticao, e cada prev_hash aponta para o hash anterior
        Long dupes = jdbc.queryForObject("""
                SELECT count(*) FROM (SELECT chain_seq FROM audit_log WHERE tenant_id = ? AND chain_seq IS NOT NULL
                                      GROUP BY chain_seq HAVING count(*) > 1) d""", Long.class, TenantContext.DEFAULT_TENANT_ID);
        assertEquals(0L, dupes);
        Long maxSeq = jdbc.queryForObject("SELECT max(chain_seq) FROM audit_log WHERE tenant_id = ?", Long.class,
                TenantContext.DEFAULT_TENANT_ID);
        assertEquals(r.verifiedRecords(), maxSeq);
        assertEquals(maxSeq, r.headSeq());
    }

    @Test
    void chainsAreIndependentPerTenant() {
        long defaultBefore = integrity.verify().verifiedRecords();
        audit("DEFAULT_ONLY");
        AuditIntegrityResponse platform = TenantContext.callAs(TenantContext.PLATFORM_TENANT_ID, () -> {
            audit("PLATFORM_ONLY");
            return integrity.verify();
        });
        assertTrue(platform.ok());
        assertTrue(platform.verifiedRecords() >= 2, "genesis + registro");
        assertEquals(defaultBefore + 1, integrity.verify().verifiedRecords());
    }

    @Test
    void tamperingWithARecordIsDetectedAndPointsToIt() throws Exception {
        audit("BEFORE_TAMPER");
        UUID victim = jdbc.queryForObject("SELECT id FROM audit_log WHERE tenant_id = ? AND action = 'BEFORE_TAMPER' "
                + "ORDER BY chain_seq DESC LIMIT 1", UUID.class, TenantContext.DEFAULT_TENANT_ID);
        audit("AFTER_TAMPER");
        assertTrue(integrity.verify().ok());
        try {
            admin("ALTER TABLE audit_log DISABLE TRIGGER audit_log_no_update_delete",
                    "UPDATE audit_log SET new_value = '{\"b\":\"adulterado\"}'::jsonb WHERE id = '" + victim + "'",
                    "ALTER TABLE audit_log ENABLE TRIGGER audit_log_no_update_delete");
            AuditIntegrityResponse r = integrity.verify();
            assertFalse(r.ok());
            assertEquals("HASH_MISMATCH", r.firstDivergence().reason());
            assertEquals(victim, r.firstDivergence().recordId());

            // o job diario reage: evento de seguranca + registro na trilha
            long eventsBefore = securityEvents.count();
            job.verifyAll();
            assertTrue(securityEvents.count() > eventsBefore);
            assertTrue(securityEvents.findAll().stream().anyMatch(e -> SecurityEvent.AUDIT_CHAIN_BROKEN.equals(e.getEventType())));
        } finally {
            admin("ALTER TABLE audit_log DISABLE TRIGGER audit_log_no_update_delete",
                    "UPDATE audit_log SET new_value = '{\"b\":\"x\"}'::jsonb WHERE id = '" + victim + "'",
                    "ALTER TABLE audit_log ENABLE TRIGGER audit_log_no_update_delete");
        }
        // restaurado o conteudo, os hashes voltam a conferir (o registro AUDIT_CHAIN_BROKEN do job tambem esta encadeado)
        assertTrue(integrity.verify().ok());
    }

    @Test
    void headAndAnchorTamperingAreDetected() throws Exception {
        audit("FOR_ANCHOR");
        assertNotNull(tx.execute(s -> integrity.anchor()));
        assertTrue(integrity.verify().ok());
        String realHead = jdbc.queryForObject("SELECT head_hash FROM audit_chain_heads WHERE tenant_id = ?", String.class,
                TenantContext.DEFAULT_TENANT_ID);
        try {
            admin("ALTER TABLE audit_chain_heads DISABLE TRIGGER audit_chain_heads_guard",
                    "UPDATE audit_chain_heads SET head_hash = repeat('0', 64) WHERE tenant_id = '" + TenantContext.DEFAULT_TENANT_ID + "'");
            AuditIntegrityResponse r = integrity.verify();
            assertFalse(r.ok());
            assertEquals("HEAD_MISMATCH", r.firstDivergence().reason());
        } finally {
            admin("UPDATE audit_chain_heads SET head_hash = '" + realHead + "' WHERE tenant_id = '" + TenantContext.DEFAULT_TENANT_ID + "'",
                    "ALTER TABLE audit_chain_heads ENABLE TRIGGER audit_chain_heads_guard");
        }
        assertTrue(integrity.verify().ok());
    }

    @Test
    void applicationRoleCannotEditHeadsOrAnchorsDirectly() {
        audit("GUARD");
        assertThrows(Exception.class, () -> jdbc.update("UPDATE audit_chain_heads SET head_hash = repeat('1', 64)"));
        assertThrows(Exception.class, () -> jdbc.update("DELETE FROM audit_chain_heads"));
        tx.execute(s -> integrity.anchor());
        assertThrows(Exception.class, () -> jdbc.update("UPDATE audit_chain_anchors SET head_hash = 'x'"));
        assertThrows(Exception.class, () -> jdbc.update("DELETE FROM audit_chain_anchors"));
    }
}
