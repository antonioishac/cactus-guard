package br.com.cactus.guard.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Migracao V49 sobre banco COM DADOS (como o banco de dev): migra ate a V44, carrega transacoes em varios meses,
 * estorno, alerta e comunicacao, aplica as migrations restantes e confere linhas, datas das filhas e FKs.
 */
@EnabledIf(value = "dockerAvailable", disabledReason = "Docker indisponivel")
class TransactionPartitionMigrationIT {

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    private static PostgreSQLContainer pg;

    @BeforeAll
    static void start() {
        if (dockerAvailable()) {
            pg = new PostgreSQLContainer("postgres:16-alpine");
            pg.start();
        }
    }

    @AfterAll
    static void stop() {
        if (pg != null) pg.stop();
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations("classpath:db/migration").target(target).load();
    }

    @Test
    void migrationPreservesRowsAndForeignKeys() throws Exception {
        flyway("44").migrate();
        String tenant = "00000000-0000-0000-0000-000000000001";
        UUID party = UUID.randomUUID();
        UUID t1 = UUID.randomUUID(), t2 = UUID.randomUUID(), t3 = UUID.randomUUID(), rev = UUID.randomUUID();
        UUID alert = UUID.randomUUID(), comm = UUID.randomUUID();
        try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
             Statement s = c.createStatement()) {
            s.execute("INSERT INTO parties (id, type, document, name, tenant_id) VALUES ('" + party + "', 'PF', '12345678901', 'P', '" + tenant + "')");
            String ins = "INSERT INTO transactions (id, tenant_id, party_id, type, amount, transaction_date) VALUES ('%s', '" + tenant
                    + "', '" + party + "', 'PIX', 10, '%s')";
            s.execute(String.format(ins, t1, "2026-01-15T10:00:00Z"));
            s.execute(String.format(ins, t2, "2026-09-02T10:00:00Z"));
            s.execute(String.format(ins, t3, "2026-11-05T10:00:00Z"));
            s.execute("INSERT INTO transactions (id, tenant_id, party_id, type, amount, transaction_date, reversal_of_id) VALUES ('" + rev
                    + "', '" + tenant + "', '" + party + "', 'PIX', 10, '2026-11-06T10:00:00Z', '" + t1 + "')");
            s.execute("INSERT INTO alerts (id, tenant_id, type, severity, title) VALUES ('" + alert + "', '" + tenant + "', 'RULE', 'LOW', 't')");
            s.execute("INSERT INTO alert_transactions (tenant_id, alert_id, transaction_id) VALUES ('" + tenant + "', '" + alert + "', '" + t2 + "')");
            s.execute("INSERT INTO siscoaf_communications (id, tenant_id, type, status, transaction_id) VALUES ('" + comm + "', '" + tenant
                    + "', 'COE', 'DRAFT', '" + t3 + "')");
        }

        flyway("latest").migrate();

        try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
             Statement s = c.createStatement()) {
            assertEquals(4L, one(s, "SELECT count(*) FROM transactions"));
            assertEquals("transactions_p202601", str(s, "SELECT tableoid::regclass::text FROM transactions WHERE id = '" + t1 + "'"));
            assertEquals("transactions_p202609", str(s, "SELECT tableoid::regclass::text FROM transactions WHERE id = '" + t2 + "'"));
            assertEquals(0L, one(s, "SELECT transactions_default_partition_rows()"));
            assertEquals("p", str(s, "SELECT relkind::text FROM pg_class WHERE oid = 'public.transactions'::regclass"));
            assertEquals(1L, one(s, "SELECT count(*) FROM transactions WHERE id = '" + rev + "' AND reversal_of_date = '2026-01-15T10:00:00Z'"));
            assertEquals(1L, one(s, "SELECT count(*) FROM alert_transactions WHERE transaction_date = '2026-09-02T10:00:00Z'"));
            assertEquals(1L, one(s, "SELECT count(*) FROM siscoaf_communications WHERE transaction_date = '2026-11-05T10:00:00Z'"));
            assertEquals(3L, one(s, "SELECT count(*) FROM pg_constraint WHERE contype = 'f' AND confrelid = 'public.transactions'::regclass AND conparentid = 0"));
            assertEquals(1L, one(s, "SELECT count(*) FROM pg_class WHERE oid = 'public.transactions'::regclass AND relforcerowsecurity"));
            assertNull(str(s, "SELECT to_regclass('public.transactions_legacy')::text"));
            assertThrows(java.sql.SQLException.class, () -> s.execute("DELETE FROM transactions WHERE id = '" + t2 + "'"));
        }
    }

    private static long one(Statement s, String sql) throws Exception {
        try (ResultSet rs = s.executeQuery(sql)) { rs.next(); return rs.getLong(1); }
    }

    private static String str(Statement s, String sql) throws Exception {
        try (ResultSet rs = s.executeQuery(sql)) { rs.next(); return rs.getString(1); }
    }
}
