package br.com.cactus.guard.tenant;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.RoleRepository;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.auth.service.JwtService;
import br.com.cactus.guard.case_.service.CaseNumberService;
import br.com.cactus.guard.common.util.DocumentHash;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.job.PepExpiryJob;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import br.com.cactus.guard.rule.service.MonitoringRuleService;
import br.com.cactus.guard.support.AbstractPostgresIT;
import br.com.cactus.guard.tenant.entity.Tenant;
import br.com.cactus.guard.tenant.repository.TenantRepository;
import br.com.cactus.guard.tenant.service.TenantAccessService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Prova de isolamento entre tenants contra PostgreSQL real, com a aplicacao rodando como papel
 * NOSUPERUSER/NOBYPASSRLS (ver {@link AbstractPostgresIT}): o tenant A nao le, nao altera e nao apaga dados do
 * tenant B por repository, JPQL, SQL nativo e API; sem tenant nada e visivel (fail-closed).
 */
@AutoConfigureMockMvc
class TenantIsolationIT extends AbstractPostgresIT {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate tx;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PartyRepository partyRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private MonitoringRuleRepository ruleRepository;
    @Autowired private MonitoringRuleService ruleService;
    @Autowired private CaseNumberService caseNumberService;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TenantAccessService tenantAccess;
    @Autowired private PepExpiryJob pepExpiryJob;
    @PersistenceContext private EntityManager em;

    record T(UUID id, String code, String username, UUID userId, String token) { }

    // ------------------------------------------------------------------ fixtures

    private T newTenant(String label, String... roles) {
        UUID id = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        tenantRepository.save(Tenant.builder().id(id).code("it-" + label + "-" + suffix)
                .name("IT " + label).regulatorySegment("BANK").build());
        String username = label + "-" + suffix;
        String[] r = roles.length == 0 ? new String[]{"ANALYST", "SUPERVISOR"} : roles;
        UUID userId = TenantContext.callAs(id, () -> userRepository.save(User.builder()
                .username(username).email(username + "@it.local").password(passwordEncoder.encode("x"))
                .name(label).active(true)
                .roles(Set.copyOf(java.util.Arrays.stream(r)
                        .map(n -> roleRepository.findByName(n).orElseThrow()).toList()))
                .build()).getId());
        String token = jwtService.generateAccessToken(userId, username, List.of(r), id);
        return new T(id, "it-" + label + "-" + suffix, username, userId, token);
    }

    private static String cpf() {
        return String.format("%011d", ThreadLocalRandom.current().nextLong(10_000_000_000L, 99_999_999_999L));
    }

    private UUID saveParty(T t, String document, String name) {
        return TenantContext.callAs(t.id(), () -> partyRepository.save(Party.builder()
                .type("PF").document(document).documentHash(DocumentHash.of(document)).name(name)
                .status("ACTIVE").riskLevel("LOW").build()).getId());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String idFrom(MvcResult r) throws Exception {
        Matcher m = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"").matcher(r.getResponse().getContentAsString());
        assertTrue(m.find(), "id ausente na resposta");
        return m.group(1);
    }

    // ------------------------------------------------------------------ pre-condicao

    @Test
    void applicationRunsWithAnRoleThatCannotBypassRls() {
        Boolean privileged = jdbc.queryForObject(
                "select rolsuper or rolbypassrls from pg_roles where rolname = current_user", Boolean.class);
        assertEquals(Boolean.FALSE, privileged, "o IT so prova RLS se o papel de runtime nao ignora as policies");
        // FORCE ROW LEVEL SECURITY em todas as tabelas de cliente
        Integer withoutForce = jdbc.queryForObject("""
                select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = 'public' and c.relkind = 'r' and not c.relforcerowsecurity
                  and exists (select 1 from information_schema.columns col
                              where col.table_name = c.relname and col.column_name = 'tenant_id')
                  and c.relname <> 'refresh_tokens'""", Integer.class);
        assertEquals(0, withoutForce, "tabela com tenant_id sem FORCE ROW LEVEL SECURITY");
    }

    // ------------------------------------------------------------------ repository / JPQL

    @Test
    void repositoryCannotReadUpdateOrDeleteOtherTenantsRows() {
        T a = newTenant("repoa");
        T b = newTenant("repob");
        String doc = cpf();
        UUID partyA = saveParty(a, doc, "Parte do tenant A");

        TenantContext.runAs(b.id(), () -> {
            assertTrue(partyRepository.findById(partyA).isEmpty(), "findById cruzou tenant");
            assertTrue(partyRepository.findByDocumentHash(DocumentHash.of(doc)).isEmpty(), "busca por hash cruzou tenant");
            assertFalse(partyRepository.existsByDocumentHash(DocumentHash.of(doc)));
            assertEquals(0, partyRepository.count());
            assertTrue(partyRepository.findAll().isEmpty());
            partyRepository.deleteById(partyA); // no-op: a linha e invisivel
            assertEquals(0, partyRepository.searchIdsFullText("Parte", 10).size(), "busca full-text nativa cruzou tenant");
        });

        TenantContext.runAs(a.id(), () -> {
            Party p = partyRepository.findById(partyA).orElseThrow();
            assertEquals("Parte do tenant A", p.getName());
            assertEquals(a.id(), p.getTenantId());
            assertEquals(1, partyRepository.searchIdsFullText("Parte", 10).size());
        });
    }

    @Test
    void jpqlJoinsAndBulkDmlAreScopedToTheTenant() {
        T a = newTenant("jpqla");
        T b = newTenant("jpqlb");
        UUID partyA = saveParty(a, cpf(), "Parte JPQL A");
        TenantContext.runAs(a.id(), () -> alertRepository.save(Alert.builder()
                .party(partyRepository.findById(partyA).orElseThrow())
                .type("TEST").severity("LOW").title("Alerta do tenant A").build()));

        TenantContext.runAs(b.id(), () -> tx.executeWithoutResult(s -> {
            assertEquals(0, em.createQuery("select a from Alert a join a.party p where p.id = :id", Alert.class)
                    .setParameter("id", partyA).getResultList().size(), "JOIN JPQL cruzou tenant");
            assertEquals(0L, em.createQuery("select count(p) from Party p where p.id = :id", Long.class)
                    .setParameter("id", partyA).getSingleResult());
            int updated = em.createQuery("update Party p set p.name = 'invadido' where p.id = :id")
                    .setParameter("id", partyA).executeUpdate();
            assertEquals(0, updated, "UPDATE em lote JPQL alterou linha de outro tenant");
            int deleted = em.createQuery("delete from Alert a where a.title = 'Alerta do tenant A'").executeUpdate();
            assertEquals(0, deleted, "DELETE em lote JPQL apagou linha de outro tenant");
        }));

        TenantContext.runAs(a.id(), () -> {
            assertEquals("Parte JPQL A", partyRepository.findById(partyA).orElseThrow().getName());
            assertEquals(1, alertRepository.findAll().stream().filter(x -> "Alerta do tenant A".equals(x.getTitle())).count());
        });
    }

    // ------------------------------------------------------------------ SQL nativo (RLS)

    @Test
    void nativeSqlIsBlockedByRowLevelSecurity() {
        T a = newTenant("sqla");
        T b = newTenant("sqlb");
        UUID partyA = saveParty(a, cpf(), "Parte SQL A");
        TenantContext.runAs(a.id(), () -> jdbc.update(
                "insert into watchlist_entries(document, name, reason, tenant_id) values ('1','WL A','motivo',?)", a.id()));

        TenantContext.runAs(b.id(), () -> {
            assertEquals(0L, jdbc.queryForObject("select count(*) from parties where id = ?", Long.class, partyA));
            assertEquals(0L, jdbc.queryForObject("select count(*) from parties", Long.class));
            assertEquals(0L, jdbc.queryForObject("select count(*) from watchlist_entries where name = 'WL A'", Long.class));
            assertEquals(0, jdbc.update("update parties set name = 'invadido' where id = ?", partyA));
            assertEquals(0, jdbc.update("delete from parties where id = ?", partyA));
            assertEquals(0, jdbc.update("delete from watchlist_entries"));
            // escrever no tenant alheio: WITH CHECK da policy rejeita
            assertThrows(DataAccessException.class, () -> jdbc.update(
                    "insert into watchlist_entries(document, name, reason, tenant_id) values ('2','forjado','x',?)", a.id()));
            // user_roles herda o isolamento de users
            Long visibleRoles = jdbc.queryForObject("select count(*) from user_roles", Long.class);
            assertEquals(Long.valueOf(2), visibleRoles, "user_roles deve mostrar so os perfis dos usuarios do proprio tenant");
        });

        TenantContext.runAs(a.id(), () -> {
            assertEquals("Parte SQL A", jdbc.queryForObject("select name from parties where id = ?", String.class, partyA));
            assertEquals(1L, jdbc.queryForObject("select count(*) from watchlist_entries where name = 'WL A'", Long.class));
        });
    }

    @Test
    void withoutTenantNothingIsVisibleOrWritable() {
        T a = newTenant("none");
        UUID partyA = saveParty(a, cpf(), "Parte sem tenant");
        TenantContext.clear();

        assertEquals(0L, jdbc.queryForObject("select count(*) from parties", Long.class));
        assertEquals(0L, jdbc.queryForObject("select count(*) from users", Long.class));
        assertEquals(0L, jdbc.queryForObject("select count(*) from audit_log", Long.class));
        assertTrue(partyRepository.findById(partyA).isEmpty());
        assertEquals(0, partyRepository.count());
        assertEquals(0, jdbc.update("update parties set name = 'x' where id = ?", partyA));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "insert into watchlist_entries(document, name, reason, tenant_id) values ('3','sem tenant','x',?)", a.id()));
        assertThrows(Exception.class, () -> saveParty(new T(TenantContext.NO_TENANT, "", "", null, ""), cpf(), "orfa"));
    }

    @Test
    void pooledConnectionsNeverCarryTheTenantOfThePreviousUse() {
        T a = newTenant("poola");
        T b = newTenant("poolb");
        UUID partyA = saveParty(a, cpf(), "Parte pool A");
        UUID partyB = saveParty(b, cpf(), "Parte pool B");
        for (int i = 0; i < 60; i++) {
            TenantContext.runAs(a.id(), () -> assertEquals(1L, jdbc.queryForObject("select count(*) from parties where id = ?", Long.class, partyA)));
            TenantContext.runAs(b.id(), () -> assertEquals(0L, jdbc.queryForObject("select count(*) from parties where id = ?", Long.class, partyA)));
            TenantContext.runAs(b.id(), () -> assertEquals(1L, jdbc.queryForObject("select count(*) from parties where id = ?", Long.class, partyB)));
            TenantContext.clear();
            assertEquals(0L, jdbc.queryForObject("select count(*) from parties where id in (?, ?)", Long.class, partyA, partyB));
            TenantContext.set(TenantContext.DEFAULT_TENANT_ID);
        }
    }

    @Test
    void tenantCannotBeSwitchedInsideAnActiveTransaction() {
        T a = newTenant("txa");
        T b = newTenant("txb");
        TenantContext.runAs(a.id(), () -> tx.executeWithoutResult(s ->
                assertThrows(IllegalStateException.class, () -> TenantContext.runAs(b.id(), () -> { }))));
    }

    // ------------------------------------------------------------------ API

    @Test
    void apiIsolatesPartiesAndAllowsTheSameDocumentInTwoTenants() throws Exception {
        T a = newTenant("apia");
        T b = newTenant("apib");
        String doc = cpf();
        String body = "{\"type\":\"PF\",\"document\":\"" + doc + "\",\"name\":\"Mesmo Documento\"}";

        // mesmo CPF nos dois tenants: hash igual NAO colide (unicidade e por tenant)
        String idA = idFrom(mvc.perform(post("/api/v1/parties").header("Authorization", bearer(a.token()))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn());
        String idB = idFrom(mvc.perform(post("/api/v1/parties").header("Authorization", bearer(b.token()))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn());
        assertNotEquals(idA, idB);
        // duplicado dentro do MESMO tenant continua proibido
        mvc.perform(post("/api/v1/parties").header("Authorization", bearer(a.token()))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnprocessableContent());

        // busca por documento enxerga so a propria parte
        mvc.perform(get("/api/v1/parties").param("document", doc).header("Authorization", bearer(a.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(idA));
        mvc.perform(get("/api/v1/parties").param("document", doc).header("Authorization", bearer(b.token())))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(idB));

        // A nao alcanca a parte de B por id, nem altera, nem apaga
        mvc.perform(get("/api/v1/parties/" + idB).header("Authorization", bearer(a.token()))).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/parties/" + idB).header("Authorization", bearer(a.token()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"PF\",\"name\":\"Invadido\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/parties/" + idB).header("Authorization", bearer(a.token()))).andExpect(status().is4xxClientError()); // legado: "nao encontrado" no DELETE sai como 400
        mvc.perform(post("/api/v1/parties/" + idB + "/reveal").header("Authorization", bearer(a.token()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fields\":[\"document\"],\"reason\":\"tentativa de acesso cruzado entre tenants\"}"))
                .andExpect(status().isNotFound());

        // B segue intacto
        mvc.perform(get("/api/v1/parties/" + idB).header("Authorization", bearer(b.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Mesmo Documento"));
        // listagens: cada um ve 1
        mvc.perform(get("/api/v1/parties").header("Authorization", bearer(a.token())))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void tokenWithoutValidActiveTenantIsForbidden() throws Exception {
        T a = newTenant("tka");
        T b = newTenant("tkb");

        // claim ausente
        String noClaim = io.jsonwebtoken.Jwts.builder().issuer("cactusguard").subject(a.username())
                .claim("roles", List.of("ANALYST")).expiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "it-secret-key-for-integration-tests-only-0123456789".getBytes())).compact();
        mvc.perform(get("/api/v1/parties").header("Authorization", bearer(noClaim))).andExpect(status().isForbidden());

        // tenant inexistente
        String ghost = jwtService.generateAccessToken(a.userId(), a.username(), List.of("ANALYST"), UUID.randomUUID());
        mvc.perform(get("/api/v1/parties").header("Authorization", bearer(ghost))).andExpect(status().isForbidden());

        // claim de um tenant com usuario de outro: o usuario nao existe la -> nao autenticado
        String crossed = jwtService.generateAccessToken(a.userId(), a.username(), List.of("ANALYST"), b.id());
        mvc.perform(get("/api/v1/parties").header("Authorization", bearer(crossed))).andExpect(status().isUnauthorized());

        // tenant suspenso
        tenantRepository.findById(a.id()).ifPresent(t -> {
            t.setStatus(Tenant.SUSPENDED);
            tenantRepository.save(t);
        });
        tenantAccess.invalidate(a.id());
        mvc.perform(get("/api/v1/parties").header("Authorization", bearer(a.token()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/parties").header("Authorization", bearer(b.token()))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ unicidade por tenant

    @Test
    void sameRuleCodeAndCaseNumberExistInEveryTenant() {
        T a = newTenant("uqa");
        T b = newTenant("uqb");
        int copiedA = TenantContext.callAs(a.id(), ruleService::provisionFromTemplates);
        int copiedB = TenantContext.callAs(b.id(), ruleService::provisionFromTemplates);
        assertTrue(copiedA >= 8, "catalogo de regras-modelo deveria trazer as regras de V5/V16/V19/V27");
        assertEquals(copiedA, copiedB);
        UUID ruleA = TenantContext.callAs(a.id(), () -> ruleRepository.findByCode("WATCHLIST_MATCH").orElseThrow().getId());
        UUID ruleB = TenantContext.callAs(b.id(), () -> ruleRepository.findByCode("WATCHLIST_MATCH").orElseThrow().getId());
        assertNotEquals(ruleA, ruleB);
        // idempotente
        assertEquals(0, (int) TenantContext.callAs(a.id(), ruleService::provisionFromTemplates));
        // a regra de A nao e visivel em B
        TenantContext.runAs(b.id(), () -> assertTrue(ruleRepository.findById(ruleA).isEmpty()));

        // contador de numero de case reinicia por tenant
        String a1 = TenantContext.callAs(a.id(), caseNumberService::next);
        String a2 = TenantContext.callAs(a.id(), caseNumberService::next);
        String b1 = TenantContext.callAs(b.id(), caseNumberService::next);
        assertTrue(a1.endsWith("000001") && a2.endsWith("000002"), a1 + " " + a2);
        assertTrue(b1.endsWith("000001"), b1);
    }

    // ------------------------------------------------------------------ jobs

    @Test
    void scheduledJobIteratesEveryActiveTenantWithoutCrossingData() {
        T a = newTenant("joba");
        T b = newTenant("jobb");
        UUID pa = pepExpired(a);
        UUID pb = pepExpired(b);

        pepExpiryJob.run(); // sem tenant no contexto: o job itera os tenants ativos

        assertEquals("EXPIRED", TenantContext.callAs(a.id(), () -> partyRepository.findById(pa).orElseThrow().getPepStatus()));
        assertEquals("EXPIRED", TenantContext.callAs(b.id(), () -> partyRepository.findById(pb).orElseThrow().getPepStatus()));
        // os alertas de mudanca de PEP ficaram cada um no seu tenant
        for (T t : List.of(a, b)) {
            TenantContext.runAs(t.id(), () -> assertEquals(1, alertRepository.findAll().stream()
                    .filter(x -> "PEP".equals(x.getType())).count()));
        }
    }

    private UUID pepExpired(T t) {
        return TenantContext.callAs(t.id(), () -> {
            Party p = Party.builder().type("PF").document(cpf()).name("PEP vencido").status("ACTIVE").riskLevel("LOW")
                    .pep(true).pepStatus("ACTIVE").pepExpiryDate(LocalDate.now().minusDays(3)).build();
            p.setDocumentHash(DocumentHash.of(p.getDocument()));
            return partyRepository.save(p).getId();
        });
    }

    // ------------------------------------------------------------------ plataforma

    @Test
    void platformAdminProvisionsSuspendsAndCannotTouchBusinessData() throws Exception {
        T platform = platformAdmin();
        String code = "cliente-" + UUID.randomUUID().toString().substring(0, 8);
        String adminUser = "adm-" + code;
        String cnpj = String.format("%014d", ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L));

        MvcResult created = mvc.perform(post("/api/v1/platform/tenants").header("Authorization", bearer(platform.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"Banco Cliente\",\"cnpj\":\"" + cnpj + "\","
                                + "\"regulatorySegment\":\"BANK\",\"adminUsername\":\"" + adminUser + "\","
                                + "\"adminEmail\":\"adm@" + code + ".com\",\"adminName\":\"Admin Cliente\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.temporaryPassword").isString())
                .andExpect(jsonPath("$.rulesCopied").isNumber())
                .andExpect(jsonPath("$.tenant.cnpjMasked").value("**********" + cnpj.substring(10)))
                .andReturn();
        String body = created.getResponse().getContentAsString();
        Matcher pw = Pattern.compile("\"temporaryPassword\":\"([^\"]+)\"").matcher(body);
        assertTrue(pw.find());
        String tenantId = idFrom(created);

        // cnpj/codigo/username repetidos sao recusados
        mvc.perform(post("/api/v1/platform/tenants").header("Authorization", bearer(platform.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"x\",\"cnpj\":\"" + cnpj + "\","
                                + "\"regulatorySegment\":\"BANK\",\"adminUsername\":\"outro\","
                                + "\"adminEmail\":\"o@o.com\",\"adminName\":\"x\"}"))
                .andExpect(status().isUnprocessableContent());

        // o primeiro admin loga com a senha temporaria e enxerga as regras-modelo copiadas (somente as dele)
        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + adminUser + "\",\"password\":\"" + pw.group(1) + "\"}"))
                .andExpect(status().isOk()).andReturn();
        Matcher at = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(login.getResponse().getContentAsString());
        assertTrue(at.find());
        String adminToken = at.group(1);
        assertEquals(tenantId, jwtService.extractTenantId(adminToken).orElseThrow().toString());
        mvc.perform(get("/api/v1/rules").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").isNumber());
        mvc.perform(get("/api/v1/parties").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        // admin de tenant nao opera a plataforma
        mvc.perform(get("/api/v1/platform/tenants").header("Authorization", bearer(adminToken))).andExpect(status().isForbidden());

        // PLATFORM_ADMIN nao acessa dados de negocio de nenhum tenant
        mvc.perform(get("/api/v1/parties").header("Authorization", bearer(platform.token()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/rules").header("Authorization", bearer(platform.token()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/alerts").header("Authorization", bearer(platform.token()))).andExpect(status().isForbidden());

        // lista de plataforma
        mvc.perform(get("/api/v1/platform/tenants").header("Authorization", bearer(platform.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());

        // suspensao: justificativa obrigatoria; depois disso o token vigente e o login passam a 403
        mvc.perform(post("/api/v1/platform/tenants/" + tenantId + "/suspend").header("Authorization", bearer(platform.token()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"curta\"}")).andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/v1/platform/tenants/" + tenantId + "/suspend").header("Authorization", bearer(platform.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"inadimplencia contratual confirmada pelo comercial\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        mvc.perform(get("/api/v1/parties").header("Authorization", bearer(adminToken))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + adminUser + "\",\"password\":\"" + pw.group(1) + "\"}"))
                .andExpect(status().isForbidden());

        // reativacao restabelece o acesso
        mvc.perform(post("/api/v1/platform/tenants/" + tenantId + "/reactivate").header("Authorization", bearer(platform.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"pagamento regularizado e contrato vigente\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/v1/parties").header("Authorization", bearer(adminToken))).andExpect(status().isOk());

        // auditoria: acao de plataforma ficou no audit_log do tenant 'platform'
        TenantContext.runAs(TenantContext.PLATFORM_TENANT_ID, () -> assertTrue(jdbc.queryForObject(
                "select count(*) from audit_log where action in ('TENANT_PROVISIONED','TENANT_SUSPENDED','TENANT_REACTIVATED')",
                Long.class) >= 3));
    }

    private T platformAdmin() {
        String username = "plat-" + UUID.randomUUID().toString().substring(0, 8);
        UUID id = TenantContext.callAs(TenantContext.PLATFORM_TENANT_ID, () -> userRepository.save(User.builder()
                .username(username).email(username + "@platform.local").password(passwordEncoder.encode("x"))
                .name("Platform").active(true)
                .roles(Set.of(roleRepository.findByName("PLATFORM_ADMIN").orElseThrow())).build()).getId());
        return new T(TenantContext.PLATFORM_TENANT_ID, "platform", username, id,
                jwtService.generateAccessToken(id, username, List.of("PLATFORM_ADMIN"), TenantContext.PLATFORM_TENANT_ID));
    }
}
