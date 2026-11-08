package br.com.cactus.guard.approval;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.approval.dto.PendingApprovalsResponse;
import br.com.cactus.guard.approval.service.PendingApprovalsService;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.case_.entity.Case;
import br.com.cactus.guard.case_.repository.CaseRepository;
import br.com.cactus.guard.notification.entity.Notification;
import br.com.cactus.guard.notification.repository.NotificationRepository;
import br.com.cactus.guard.notification.service.NotificationService;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.report.entity.ReportJob;
import br.com.cactus.guard.report.repository.ReportJobRepository;
import br.com.cactus.guard.report.service.ReportService;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.rule.service.MonitoringRuleService;
import br.com.cactus.guard.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Fila de aprovacao, notificacoes, relatorios e contratos HTTP (roles e multipart) desta trilha. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "cases.evidence.dir=target/test-case-evidences-http")
class ApprovalsAndHttpContractTest {

    @Autowired private MockMvc mvc;
    @Autowired private PendingApprovalsService approvals;
    @Autowired private CaseRepository caseRepository;
    @Autowired private PartyRepository partyRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private MonitoringRuleService ruleService;
    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ReportJobRepository reportJobRepository;
    @Autowired private ReportService reportService;

    @AfterEach
    void logout() {
        TestUsers.logout();
    }

    @Test
    void pendingApprovalsAggregatesCasesRulesAndPepsAndFlagsOwnItems() {
        User me = TestUsers.ensure(userRepository, TestUsers.unique("sup"));
        User other = TestUsers.ensure(userRepository, TestUsers.unique("other"));
        Party pep = partyRepository.save(Party.builder().type("PF")
                .document(UUID.randomUUID().toString().replace("-", "").substring(0, 11))
                .name("PEP pendente").status("ACTIVE").pep(true).pepStatus("ACTIVE").pepCategory("NATIONAL").riskLevel("HIGH").build());
        Case own = caseRepository.save(Case.builder().status("PENDING_APPROVAL").decision("COMMUNICATE")
                .decidedBy(me).analysisDeadline(Instant.now().plusSeconds(3600)).build());
        Case theirs = caseRepository.save(Case.builder().status("PENDING_APPROVAL").decision("ARCHIVE")
                .decidedBy(other).analysisDeadline(Instant.now().plusSeconds(7200)).build());
        TestUsers.login(other.getUsername(), "SUPERVISOR");
        MonitoringRule r = new MonitoringRule();
        r.setCode(TestUsers.unique("AP").toUpperCase());
        r.setName("Regra fila");
        r.setCategory("TRANSACTIONAL");
        r.setSeverity("LOW");
        r.setThresholdValue(BigDecimal.ONE);
        ruleService.create(r);

        TestUsers.login(me.getUsername(), "SUPERVISOR");
        PendingApprovalsResponse res = approvals.pending(PageRequest.of(0, 100));

        assertTrue(res.counts().cases() >= 2);
        assertTrue(res.counts().rules() >= 1);
        assertTrue(res.counts().peps() >= 1);
        assertTrue(res.cases().getContent().stream().anyMatch(c -> c.id().equals(own.getId()) && c.ownDecision()));
        assertTrue(res.cases().getContent().stream().anyMatch(c -> c.id().equals(theirs.getId()) && !c.ownDecision()));
        assertTrue(res.rules().getContent().stream().anyMatch(x -> x.code().equals(r.getCode()) && !x.ownChange()));
        var pepItem = res.peps().getContent().stream().filter(p -> p.partyId().equals(pep.getId())).findFirst().orElseThrow();
        assertTrue(pepItem.document().startsWith("*"));
    }

    @Test
    void markAllNotificationsAsReadOnlyAffectsCurrentUser() {
        User a = TestUsers.ensure(userRepository, TestUsers.unique("na"));
        User b = TestUsers.ensure(userRepository, TestUsers.unique("nb"));
        notificationService.create(a.getId(), "T", "t1", "m", null, null);
        notificationService.create(a.getId(), "T", "t2", "m", null, null);
        Notification nb = notificationService.create(b.getId(), "T", "t3", "m", null, null);

        TestUsers.login(a.getUsername(), "ANALYST");
        assertEquals(2, notificationService.countUnread());
        assertEquals(2, notificationService.markAllAsRead());
        assertEquals(0, notificationService.countUnread());
        assertFalse(notificationRepository.findById(nb.getId()).orElseThrow().isRead());
        // nao marca notificacao de outro usuario individualmente
        assertThrows(IllegalArgumentException.class, () -> notificationService.markAsRead(nb.getId()));
    }

    @Test
    void failedReportJobExposesGenericErrorMessage() {
        User u = TestUsers.ensure(userRepository, TestUsers.unique("rep"));
        TestUsers.login(u.getUsername(), "ANALYST");
        ReportJob job = reportJobRepository.save(ReportJob.builder().reportCode("X").requestedBy(u).status("FAILED")
                .format("PDF").errorMessage("Falha na geracao do relatorio").build());
        var res = reportService.findJob(job.getId());
        assertEquals("FAILED", res.getStatus());
        assertEquals("Falha na geracao do relatorio", res.getErrorMessage());
    }

    // ------------------------------------------------------------------ HTTP

    @Test
    void rejectApprovalIsForbiddenForAnalystAndValidatesBody() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/v1/cases/{id}/reject-approval", id).contentType("application/json")
                        .content("{\"justification\":\"qualquer justificativa longa o bastante\"}")
                        .with(user("analista").roles("ANALYST")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/cases/{id}/reject-approval", id).contentType("application/json")
                        .content("{}").with(user("sup").roles("SUPERVISOR")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void alertCommentsRolesAndValidation() throws Exception {
        User analyst = TestUsers.ensure(userRepository, TestUsers.unique("analyst"));
        Alert alert = alertRepository.save(Alert.builder().type("T").severity("LOW").status("OPEN").title("a")
                .createdAt(Instant.now()).build());
        mvc.perform(post("/api/v1/alerts/{id}/comments", alert.getId()).contentType("application/json")
                        .content("{\"text\":\"observacao\"}").with(user("aud").roles("AUDITOR")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/alerts/{id}/comments", alert.getId()).contentType("application/json")
                        .content("{\"text\":\"\"}").with(user(analyst.getUsername()).roles("ANALYST")))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/api/v1/alerts/{id}/comments", alert.getId()).contentType("application/json")
                        .content("{\"text\":\"observacao do analista\"}").with(user(analyst.getUsername()).roles("ANALYST")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorName").value(analyst.getUsername()));
        mvc.perform(get("/api/v1/alerts/{id}/comments", alert.getId()).with(user("aud").roles("AUDITOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void approvalsQueueIsSupervisorOnly() throws Exception {
        mvc.perform(get("/api/v1/approvals/pending").with(user("analista").roles("ANALYST")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/approvals/pending").with(user("auditor").roles("AUDITOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCaseRequiresJustificationAndAliasStillWorks() throws Exception {
        User analyst = TestUsers.ensure(userRepository, TestUsers.unique("analyst"));
        Alert alert = alertRepository.save(Alert.builder().type("T").severity("LOW").status("OPEN").title("a")
                .createdAt(Instant.now()).build());
        mvc.perform(post("/api/v1/cases").contentType("application/json")
                        .content("{\"alertId\":\"" + alert.getId() + "\"}").with(user(analyst.getUsername()).roles("ANALYST")))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/api/v1/cases").contentType("application/json")
                        .content("{\"alertId\":\"" + alert.getId() + "\",\"justification\":\"Selecionado apos triagem inicial do alerta\"}")
                        .with(user(analyst.getUsername()).roles("ANALYST")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseNumber").value(org.hamcrest.Matchers.startsWith("CG-")));
        Alert alert2 = alertRepository.save(Alert.builder().type("T").severity("LOW").status("OPEN").title("a")
                .createdAt(Instant.now()).build());
        mvc.perform(post("/api/v1/cases/from-alert/{id}", alert2.getId()).with(user(analyst.getUsername()).roles("ANALYST")))
                .andExpect(status().isCreated());
    }

    @Test
    void evidenceMultipartUploadListAndDownloadOverHttp() throws Exception {
        User analyst = TestUsers.ensure(userRepository, TestUsers.unique("analyst"));
        Alert alert = alertRepository.save(Alert.builder().type("T").severity("LOW").status("OPEN").title("a")
                .createdAt(Instant.now()).build());
        String body = mvc.perform(post("/api/v1/cases/from-alert/{id}", alert.getId()).with(user(analyst.getUsername()).roles("ANALYST")))
                .andReturn().getResponse().getContentAsString();
        String caseId = body.replaceAll(".*\"id\":\"([0-9a-f-]{36})\".*", "$1");

        byte[] pdf = "%PDF-1.4\nhttp evidence\n".getBytes();
        String ev = mvc.perform(multipart("/api/v1/cases/{id}/evidences", caseId)
                        .file(new MockMultipartFile("file", "extrato.pdf", "application/pdf", pdf))
                        .param("description", "extrato bancario")
                        .with(user(analyst.getUsername()).roles("ANALYST")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sha256").isNotEmpty())
                .andExpect(jsonPath("$.filePath").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String evId = ev.replaceAll(".*\"id\":\"([0-9a-f-]{36})\".*", "$1");

        mvc.perform(get("/api/v1/cases/{id}/evidences", caseId).with(user("aud").roles("AUDITOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].fileName").value("extrato.pdf"));
        mvc.perform(get("/api/v1/cases/{id}/evidences/{e}", caseId, evId).with(user("aud").roles("AUDITOR")))
                .andExpect(status().isOk()).andExpect(content().bytes(pdf));
        mvc.perform(get("/api/v1/cases/{id}/evidences/{e}/download", caseId, evId).with(user("aud").roles("AUDITOR")))
                .andExpect(status().isOk());
        // auditor nao anexa evidencias
        mvc.perform(multipart("/api/v1/cases/{id}/evidences", caseId)
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", pdf))
                        .with(user("aud").roles("AUDITOR")))
                .andExpect(status().isForbidden());
        // arquivo invalido
        mvc.perform(multipart("/api/v1/cases/{id}/evidences", caseId)
                        .file(new MockMultipartFile("file", "x.pdf", "application/pdf", "nao pdf".getBytes()))
                        .with(user(analyst.getUsername()).roles("ANALYST")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void rulesEndpointReturnsPageAndDashboardEndpointsRespond() throws Exception {
        mvc.perform(get("/api/v1/rules").param("includeInactive", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        mvc.perform(get("/api/v1/rules/{c}", "NAO_EXISTE_XYZ")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/v1/dashboard/metrics")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/dashboard/alerts-chart")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7));
        mvc.perform(get("/api/v1/dashboard/severity-distribution")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/dashboard/case-sla")).andExpect(status().isOk()).andExpect(jsonPath("$.analysis.onTime").exists());
        mvc.perform(get("/api/v1/alerts").param("status", "OPEN,ESCALATED").param("from", "2026-01-01").param("to", "2026-12-31"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/alerts").param("from", "ontem")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/v1/cases").param("slaStatus", "OVERDUE").param("status", "OPEN", "ESCALATED"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/siscoaf").param("status", "DRAFT", "APPROVED").param("type", "COS"))
                .andExpect(status().isOk());
    }
}
