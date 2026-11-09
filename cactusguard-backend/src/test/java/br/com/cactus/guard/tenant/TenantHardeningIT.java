package br.com.cactus.guard.tenant;

import br.com.cactus.guard.admin.service.DocumentHashMigrationRunner;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.RoleRepository;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.common.util.DocumentHash;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.support.AbstractPostgresIT;
import br.com.cactus.guard.tenant.entity.Tenant;
import br.com.cactus.guard.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Endurecimento do isolamento (V42-V44) contra PostgreSQL real, com o papel NOSUPERUSER/NOBYPASSRLS:
 * FKs compostas barram referencia cruzada entre tenants e o rehash migra hashes legados para o pepper por tenant.
 */
class TenantHardeningIT extends AbstractPostgresIT {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PartyRepository partyRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DocumentHashMigrationRunner hashMigrationRunner;

    private UUID newTenant(String label, int hashVersion) {
        UUID id = UUID.randomUUID();
        tenantRepository.save(Tenant.builder().id(id).code("hd-" + label + "-" + id.toString().substring(0, 8))
                .name("IT " + label).regulatorySegment("BANK").hashVersion(hashVersion).build());
        return id;
    }

    private UUID newUser(UUID tenant, String label) {
        String username = label + "-" + UUID.randomUUID().toString().substring(0, 8);
        return TenantContext.callAs(tenant, () -> userRepository.save(User.builder()
                .username(username).email(username + "@it.local").password(passwordEncoder.encode("x"))
                .name(label).active(true)
                .roles(Set.of(roleRepository.findByName("ANALYST").orElseThrow())).build()).getId());
    }

    private static String cpf() {
        return String.format("%011d", ThreadLocalRandom.current().nextLong(10_000_000_000L, 99_999_999_999L));
    }

    private UUID newParty(UUID tenant, String doc, String hash) {
        return TenantContext.callAs(tenant, () -> partyRepository.save(Party.builder()
                .type("PF").document(doc).documentHash(hash).name("P").status("ACTIVE").riskLevel("LOW").build()).getId());
    }

    private void insertRisk(UUID tenant, UUID partyId, UUID changedBy) {
        TenantContext.callAs(tenant, () -> jdbc.update(
                "INSERT INTO risk_history (party_id, new_score, new_level, changed_by, tenant_id) VALUES (?, 10, 'LOW', ?, ?)",
                partyId, changedBy, tenant));
    }

    @Test
    void childOfTenantACannotReferenceParentOfTenantB() {
        UUID a = newTenant("fka", 2);
        UUID b = newTenant("fkb", 2);
        UUID partyA = newParty(a, cpf(), "h" + UUID.randomUUID());
        UUID partyB = newParty(b, cpf(), "h" + UUID.randomUUID());
        UUID userB = newUser(b, "ub");

        // controle: referencia do proprio tenant funciona
        insertRisk(a, partyA, null);
        // pai de outro tenant (coluna NOT NULL party_id)
        assertThrows(DataIntegrityViolationException.class, () -> insertRisk(a, partyB, null));
        // FK anulavel (changed_by -> users) apontando para usuario de outro tenant
        assertThrows(DataIntegrityViolationException.class, () -> insertRisk(a, partyA, userB));
        // UPDATE para referencia cruzada tambem e barrado
        assertThrows(DataIntegrityViolationException.class, () -> TenantContext.callAs(a, () -> jdbc.update(
                "UPDATE risk_history SET party_id = ? WHERE party_id = ?", partyB, partyA)));
    }

    @Test
    void noSingleColumnForeignKeyBetweenTenantTablesRemains() {
        Integer single = jdbc.queryForObject("""
                select count(*) from pg_constraint c
                where c.contype = 'f' and c.connamespace = 'public'::regnamespace and cardinality(c.conkey) = 1
                  and exists (select 1 from pg_attribute t where t.attrelid = c.conrelid and t.attname = 'tenant_id' and not t.attisdropped)
                  and exists (select 1 from pg_attribute t where t.attrelid = c.confrelid and t.attname = 'tenant_id' and not t.attisdropped)
                """, Integer.class);
        assertEquals(0, single, "FK de coluna unica entre tabelas por tenant ignora o isolamento");
        Integer composite = jdbc.queryForObject("""
                select count(*) from pg_constraint c
                where c.contype = 'f' and c.connamespace = 'public'::regnamespace and cardinality(c.conkey) = 2
                  and c.conname like 'fkc\\_%'
                """, Integer.class);
        assertTrue(composite != null && composite >= 40, "FKs compostas esperadas, achadas: " + composite);
    }

    @Test
    void legacyHashesAreMigratedOnceToTheTenantPepper() {
        UUID legacy = newTenant("hv1", 1);
        UUID other = newTenant("hv2", 1);
        String doc = cpf();
        // estado anterior: hash com o pepper global
        UUID p1 = newParty(legacy, doc, DocumentHash.ofPlatform(doc));
        UUID p2 = newParty(other, doc, DocumentHash.ofPlatform(doc));

        assertTrue(hashMigrationRunner.migratePending() >= 2);

        String h1 = TenantContext.callAs(legacy, () -> jdbc.queryForObject("select document_hash from parties where id = ?", String.class, p1));
        String h2 = TenantContext.callAs(other, () -> jdbc.queryForObject("select document_hash from parties where id = ?", String.class, p2));
        assertEquals(DocumentHash.of(legacy, doc), h1);
        assertEquals(DocumentHash.of(other, doc), h2);
        assertNotEquals(h1, h2, "mesmo CPF em tenants distintos nao pode gerar o mesmo hash");

        // busca por documento continua funcionando em cada tenant (e so enxerga o proprio)
        assertEquals(p1, TenantContext.callAs(legacy, () -> partyRepository.findByDocumentHash(DocumentHash.of(doc)).orElseThrow().getId()));
        assertEquals(p2, TenantContext.callAs(other, () -> partyRepository.findByDocumentHash(DocumentHash.of(doc)).orElseThrow().getId()));
        assertEquals(Integer.valueOf(2), jdbc.queryForObject("select hash_version from tenants where id = ?", Integer.class, legacy));

        // idempotente: nada mais a migrar para estes tenants
        hashMigrationRunner.migratePending();
        assertEquals(h1, TenantContext.callAs(legacy, () -> jdbc.queryForObject("select document_hash from parties where id = ?", String.class, p1)));
    }
}
