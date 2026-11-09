package br.com.cactus.guard.transaction;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.support.AbstractPostgresIT;
import br.com.cactus.guard.tenant.TenantContext;
import br.com.cactus.guard.tenant.entity.Tenant;
import br.com.cactus.guard.tenant.repository.TenantRepository;
import br.com.cactus.guard.transaction.service.TransactionPartitionJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Particionamento de transactions (V48-V50) contra PostgreSQL real com o papel NOSUPERUSER/NOBYPASSRLS:
 * roteamento por mes, poda de particoes, RLS, FKs compostas com as filhas, DEFAULT e idempotencia do job.
 */
class TransactionPartitioningIT extends AbstractPostgresIT {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private PartyRepository partyRepository;
    @Autowired private TransactionPartitionJob job;

    private UUID newTenant(String label) {
        UUID id = UUID.randomUUID();
        tenantRepository.save(Tenant.builder().id(id).code("pt-" + label + "-" + id.toString().substring(0, 8))
                .name("IT " + label).regulatorySegment("BANK").hashVersion(2).build());
        return id;
    }

    private UUID newParty(UUID tenant) {
        String doc = String.format("%011d", ThreadLocalRandom.current().nextLong(10_000_000_000L, 99_999_999_999L));
        return TenantContext.callAs(tenant, () -> partyRepository.save(Party.builder().type("PF").document(doc)
                .documentHash("h" + UUID.randomUUID()).name("P").status("ACTIVE").riskLevel("LOW").build()).getId());
    }

    private UUID insertTx(UUID tenant, UUID party, String isoTs) {
        UUID id = UUID.randomUUID();
        TenantContext.callAs(tenant, () -> jdbc.update(
                "INSERT INTO transactions (id, tenant_id, party_id, type, amount, transaction_date) "
                        + "VALUES (?, ?, ?, 'PIX', 100, ?::timestamptz)", id, tenant, party, isoTs));
        return id;
    }

    private String partitionOf(UUID tenant, UUID txId) {
        return TenantContext.callAs(tenant, () -> jdbc.queryForObject(
                "SELECT tableoid::regclass::text FROM transactions WHERE id = ?", String.class, txId));
    }

    private UUID newAlert(UUID tenant) {
        UUID id = UUID.randomUUID();
        TenantContext.callAs(tenant, () -> jdbc.update(
                "INSERT INTO alerts (id, tenant_id, type, severity, title) VALUES (?, ?, 'RULE', 'LOW', 't')", id, tenant));
        return id;
    }

    private void linkAlert(UUID tenant, UUID alert, UUID tx) {
        TenantContext.callAs(tenant, () -> jdbc.update(
                "INSERT INTO alert_transactions (tenant_id, alert_id, transaction_id) VALUES (?, ?, ?)", tenant, alert, tx));
    }

    @Test
    void tableIsPartitionedWithCompositePkAndForcedRls() {
        assertEquals("p", jdbc.queryForObject("SELECT relkind::text FROM pg_class WHERE oid = 'public.transactions'::regclass", String.class));
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid = 'public.transactions'::regclass", Boolean.class));
        assertEquals("PRIMARY KEY (tenant_id, id, transaction_date)", jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = 'public.transactions'::regclass AND contype = 'p'",
                String.class));
        Integer parts = jdbc.queryForObject("SELECT count(*)::int FROM pg_inherits WHERE inhparent = 'public.transactions'::regclass", Integer.class);
        assertTrue(parts >= 5, "esperado >= 4 particoes mensais + DEFAULT, havia " + parts);
        Integer withoutRls = jdbc.queryForObject("""
                SELECT count(*)::int FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
                 WHERE i.inhparent = 'public.transactions'::regclass
                   AND (NOT c.relrowsecurity OR NOT c.relforcerowsecurity OR NOT EXISTS (SELECT 1 FROM pg_policy p WHERE p.polrelid = c.oid))
                """, Integer.class);
        assertEquals(0, withoutRls);
    }

    @Test
    void futureMonthRowLandsInCorrectPartitionAndDefaultStaysEmpty() {
        UUID t = newTenant("route");
        UUID p = newParty(t);
        LocalDate in2 = LocalDate.now(ZoneOffset.UTC).plusMonths(2).withDayOfMonth(15);
        UUID id = insertTx(t, p, in2 + "T12:00:00Z");
        String expected = "transactions_p" + String.format("%04d%02d", in2.getYear(), in2.getMonthValue());
        assertEquals(expected, partitionOf(t, id));
    }

    @Test
    void windowQueryPrunesPartitions() {
        UUID t = newTenant("prune");
        UUID p = newParty(t);
        jdbc.queryForObject("SELECT ensure_transaction_partition_range(DATE '2026-03-01', DATE '2026-06-30')", Integer.class);
        insertTx(t, p, "2026-03-10T10:00:00Z");
        insertTx(t, p, "2026-05-10T10:00:00Z");
        String sql = "EXPLAIN (COSTS OFF) SELECT count(*) FROM transactions WHERE tenant_id = '" + t
                + "' AND party_id = '" + p + "' AND transaction_date >= '2026-05-01T00:00:00Z' AND transaction_date < '2026-06-01T00:00:00Z'";
        String plan = String.join("\n", TenantContext.callAs(t, () -> jdbc.queryForList(sql, String.class)));
        System.out.println("EXPLAIN (janela maio/2026):\n" + plan);
        assertTrue(plan.contains("transactions_p202605"), plan);
        assertFalse(plan.contains("transactions_p202603"), plan);
        assertFalse(plan.contains("transactions_p202604"), plan);
        assertFalse(plan.contains("transactions_p202606"), plan);
        assertFalse(plan.contains("transactions_default"), plan);

        String since = "EXPLAIN (COSTS OFF) SELECT count(*) FROM transactions WHERE tenant_id = '" + t
                + "' AND transaction_date >= '2026-06-01T00:00:00Z'";
        String plan2 = String.join("\n", TenantContext.callAs(t, () -> jdbc.queryForList(since, String.class)));
        System.out.println("EXPLAIN (since jun/2026):\n" + plan2);
        assertFalse(plan2.contains("transactions_p202605"), plan2);
        assertFalse(plan2.contains("transactions_p202603"), plan2);
    }

    @Test
    void rlsIsolatesTenantsOnPartitionedTableAndPartitions() {
        UUID a = newTenant("rlsa");
        UUID b = newTenant("rlsb");
        UUID pa = newParty(a);
        UUID pb = newParty(b);
        String ts = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(2) + "T10:00:00Z";
        UUID txA = insertTx(a, pa, ts);
        insertTx(b, pb, ts);

        assertEquals(1L, TenantContext.callAs(a, () -> jdbc.queryForObject("SELECT count(*) FROM transactions", Long.class)));
        assertEquals(0L, TenantContext.callAs(b, () -> jdbc.queryForObject("SELECT count(*) FROM transactions WHERE id = ?", Long.class, txA)));
        String part = partitionOf(a, txA);
        assertEquals(0L, TenantContext.callAs(b, () -> jdbc.queryForObject("SELECT count(*) FROM " + part + " WHERE id = ?", Long.class, txA)));
        assertThrows(DataAccessException.class, () -> TenantContext.callAs(b, () -> jdbc.update(
                "INSERT INTO transactions (tenant_id, party_id, type, amount, transaction_date) VALUES (?, ?, 'PIX', 1, now())", a, pa)));
        TenantContext.clear(); // sem tenant: fail-closed, tambem em consulta DIRETA na particao e na DEFAULT
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM " + part, Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM transactions_default", Long.class));
        // o proprio tenant A enxerga a linha consultando a particao diretamente
        assertEquals(1L, TenantContext.callAs(a, () -> jdbc.queryForObject("SELECT count(*) FROM " + part + " WHERE id = ?", Long.class, txA)));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM transactions", Long.class));
    }

    @Test
    void childForeignKeysKeepIntegrity() {
        UUID t = newTenant("fk");
        UUID other = newTenant("fko");
        UUID p = newParty(t);
        UUID po = newParty(other);
        String ts = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(3) + "T09:00:00Z";
        UUID tx = insertTx(t, p, ts);
        UUID txOther = insertTx(other, po, ts);
        UUID alert = newAlert(t);

        linkAlert(t, alert, tx);
        Object date = TenantContext.callAs(t, () -> jdbc.queryForObject(
                "SELECT transaction_date FROM alert_transactions WHERE alert_id = ?", Object.class, alert));
        assertNotNull(date, "trigger deve preencher transaction_date");

        UUID alert2 = newAlert(t);
        assertThrows(DataAccessException.class, () -> linkAlert(t, alert2, UUID.randomUUID()), "transacao inexistente");
        assertThrows(DataAccessException.class, () -> linkAlert(t, alert2, txOther), "transacao de outro tenant");

        UUID rev = UUID.randomUUID();
        TenantContext.callAs(t, () -> jdbc.update(
                "INSERT INTO transactions (id, tenant_id, party_id, type, amount, transaction_date, reversal_of_id) "
                        + "VALUES (?, ?, ?, 'PIX', 5, now(), ?)", rev, t, p, tx));
        assertNotNull(TenantContext.callAs(t, () -> jdbc.queryForObject(
                "SELECT reversal_of_date FROM transactions WHERE id = ?", Object.class, rev)));
        assertThrows(DataAccessException.class, () -> TenantContext.callAs(t, () -> jdbc.update(
                "INSERT INTO transactions (tenant_id, party_id, type, amount, transaction_date, reversal_of_id) "
                        + "VALUES (?, ?, 'PIX', 5, now(), ?)", t, p, UUID.randomUUID())));

        assertThrows(DataAccessException.class, () -> TenantContext.callAs(t, () -> jdbc.update(
                "UPDATE transactions SET transaction_date = transaction_date - interval '1 day' WHERE id = ?", tx)));
        // transacao referenciada nao pode ser removida (FK fisica)
        assertThrows(DataAccessException.class, () -> TenantContext.callAs(t, () -> jdbc.update(
                "DELETE FROM transactions WHERE id = ?", tx)));
    }

    @Test
    void outOfRangeRowGoesToDefaultAndIsMovedWhenPartitionIsCreated() {
        UUID t = newTenant("dflt");
        UUID p = newParty(t);
        UUID id = insertTx(t, p, "2019-03-15T10:00:00Z");
        assertEquals("transactions_default", partitionOf(t, id));
        assertTrue(jdbc.queryForObject("SELECT transactions_default_partition_rows()", Long.class) >= 1);

        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT ensure_transaction_partition(DATE '2019-03-01')", Boolean.class));
        assertEquals("transactions_p201903", partitionOf(t, id));
        assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT ensure_transaction_partition(DATE '2019-03-20')", Boolean.class));
    }

    @Test
    void partitionJobIsIdempotent() {
        job.run();
        Integer created = jdbc.queryForObject("SELECT ensure_transaction_partitions(3, 0)", Integer.class);
        assertEquals(0, created);
        List<String> names = jdbc.queryForList("SELECT partition_name FROM transactions_partition_info()", String.class);
        assertEquals(names.size(), names.stream().distinct().count());
        assertThrows(DataAccessException.class, () -> jdbc.queryForObject("SELECT ensure_transaction_partitions(99, 0)", Integer.class));
    }
}
