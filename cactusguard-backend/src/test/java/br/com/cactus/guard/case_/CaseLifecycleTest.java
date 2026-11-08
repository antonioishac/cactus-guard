package br.com.cactus.guard.case_;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.case_.dto.CaseApprovalRequest;
import br.com.cactus.guard.case_.dto.CaseDecisionRequest;
import br.com.cactus.guard.case_.dto.CaseEscalationRequest;
import br.com.cactus.guard.case_.dto.CaseResponse;
import br.com.cactus.guard.case_.repository.CaseRepository;
import br.com.cactus.guard.case_.service.CaseService;
import br.com.cactus.guard.common.calendar.BusinessCalendar;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.siscoaf.repository.SiscoafCommunicationRepository;
import br.com.cactus.guard.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CaseLifecycleTest {

    private static final String JUST = "Analise concluida com base nos documentos e na movimentacao";

    @Autowired private CaseService caseService;
    @Autowired private CaseRepository caseRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private PartyRepository partyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditService auditService;
    @Autowired private SiscoafCommunicationRepository siscoafRepository;

    private String analyst;
    private String supervisorA;
    private String supervisorB;

    @BeforeEach
    void users() {
        analyst = TestUsers.unique("analyst");
        supervisorA = TestUsers.unique("supA");
        supervisorB = TestUsers.unique("supB");
        for (String u : new String[]{analyst, supervisorA, supervisorB}) {
            TestUsers.ensure(userRepository, u);
        }
        TestUsers.login(analyst, "ANALYST");
    }

    @AfterEach
    void cleanup() {
        TestUsers.logout();
    }

    private Party party(boolean pep, String risk, boolean edd) {
        return partyRepository.save(Party.builder()
                .type("PF")
                .document(UUID.randomUUID().toString().replace("-", "").substring(0, 11))
                .name("Case Lifecycle")
                .status("ACTIVE")
                .pep(pep)
                .riskLevel(risk)
                .eddRequired(edd)
                .build());
    }

    private Alert alert(Party p, String severity, Instant occurredAt, Instant selectionDeadline) {
        return alertRepository.save(Alert.builder()
                .party(p).type("TRANSACTIONAL").severity(severity).status("OPEN")
                .title("alert").ruleCode("STRUCTURING")
                .occurredAt(occurredAt).selectionDeadline(selectionDeadline)
                .createdAt(Instant.now()).build());
    }

    private CaseResponse newCase(Party p) {
        return caseService.createFromAlert(alert(p, "HIGH", null, null).getId());
    }

    private CaseDecisionRequest decision(String d, String j) {
        CaseDecisionRequest r = new CaseDecisionRequest();
        r.setDecision(d);
        r.setJustification(j);
        return r;
    }

    // ------------------------------------------------------------------ prazos

    @Test
    void selectionSetsSelectedAtAnd45DayAnalysisDeadline() {
        Instant occurred = Instant.now().minus(Duration.ofDays(10));
        Alert a = alert(party(false, "LOW", false), "CRITICAL", occurred, occurred.plus(Duration.ofDays(45)));
        CaseResponse c = caseService.createFromAlert(a.getId());

        assertNotNull(c.getSelectedAt());
        assertEquals(BusinessCalendar.plusCalendarDays(c.getSelectedAt(), 45), c.getAnalysisDeadline());
        assertEquals(occurred.truncatedTo(java.time.temporal.ChronoUnit.MILLIS),
                c.getOccurredAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        // SLA interno (24h para CRITICAL) <= prazo regulatorio
        assertFalse(c.getSlaDueDate().isAfter(c.getAnalysisDeadline()));
        assertTrue(c.getSlaDueDate().isBefore(c.getAnalysisDeadline()));
    }

    @Test
    void lateSelectionIsAuditedButNotBlocked() {
        Instant occurred = Instant.now().minus(Duration.ofDays(60));
        Alert a = alert(party(false, "LOW", false), "HIGH", occurred, occurred.plus(Duration.ofDays(45)));
        CaseResponse c = caseService.createFromAlert(a.getId());
        assertEquals("OPEN", c.getStatus());

        var audit = auditService.findByEntity("CASE", c.getId(), Pageable.unpaged()).getContent();
        assertTrue(audit.stream().anyMatch(l -> "CASE_LATE_SELECTION".equals(l.getAction())));
    }

    @Test
    void timelySelectionIsNotFlagged() {
        Instant occurred = Instant.now().minus(Duration.ofDays(5));
        Alert a = alert(party(false, "LOW", false), "HIGH", occurred, occurred.plus(Duration.ofDays(45)));
        CaseResponse c = caseService.createFromAlert(a.getId());
        var audit = auditService.findByEntity("CASE", c.getId(), Pageable.unpaged()).getContent();
        assertTrue(audit.stream().noneMatch(l -> "CASE_LATE_SELECTION".equals(l.getAction())));
    }

    // ------------------------------------------------------------------ decisao

    @Test
    void legacyAndUnknownDecisionsAreRejected() {
        CaseResponse c = newCase(party(false, "LOW", false));
        for (String legacy : new String[]{"APPROVED", "REJECTED", "ESCALATED", "FOO"}) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> caseService.decide(c.getId(), decision(legacy, JUST)), legacy);
            assertTrue(ex.getMessage().contains("COMMUNICATE"));
        }
    }

    @Test
    void justificationIsMandatoryWithMinimumLength() {
        CaseResponse c = newCase(party(false, "LOW", false));
        assertThrows(BusinessException.class, () -> caseService.decide(c.getId(), decision("ARCHIVE", null)));
        assertThrows(BusinessException.class, () -> caseService.decide(c.getId(), decision("ARCHIVE", "   ")));
        assertThrows(BusinessException.class, () -> caseService.decide(c.getId(), decision("ARCHIVE", "curta demais")));
        assertThrows(BusinessException.class,
                () -> caseService.decide(c.getId(), decision("ARCHIVE", "a b c d e f g h i j k l m n o p")),
                "espacos nao contam");
    }

    @Test
    void archiveOfOrdinaryCaseGoesStraightToArchived() {
        CaseResponse c = newCase(party(false, "LOW", false));
        CaseResponse d = caseService.decide(c.getId(), decision("ARCHIVE", JUST));
        assertEquals("ARCHIVED", d.getStatus());
        assertNotNull(d.getClosedAt());
        assertNotNull(d.getDecidedAt());
    }

    @Test
    void communicateAlwaysNeedsFourEyesApproval() {
        CaseResponse c = newCase(party(false, "LOW", false));
        CaseResponse d = caseService.decide(c.getId(), decision("COMMUNICATE", JUST));
        assertEquals("PENDING_APPROVAL", d.getStatus());
        assertNull(d.getCommunicationDeadline());
        assertTrue(siscoafRepository.findByCaseEntityIdAndType(c.getId(), "COS").isEmpty(),
                "nenhuma comunicacao antes da aprovacao");
    }

    @Test
    void sensitiveCasesNeedApprovalEvenWhenArchived() {
        for (Party p : new Party[]{party(true, "LOW", false), party(false, "HIGH", false),
                party(false, "CRITICAL", false), party(false, "LOW", true)}) {
            CaseResponse c = newCase(p);
            assertEquals("PENDING_APPROVAL",
                    caseService.decide(c.getId(), decision("ARCHIVE", JUST)).getStatus());
        }
    }

    @Test
    void eddFlagIsCopiedFromParty() {
        assertTrue(newCase(party(false, "LOW", true)).isEdd());
        assertFalse(newCase(party(false, "LOW", false)).isEdd());
    }

    // ------------------------------------------------------------------ quatro olhos

    @Test
    void approverMustBeDifferentSupervisor() {
        CaseResponse c = newCase(party(false, "LOW", false));
        TestUsers.login(supervisorA, "SUPERVISOR");
        caseService.decide(c.getId(), decision("COMMUNICATE", JUST));

        // mesmo usuario
        BusinessException same = assertThrows(BusinessException.class,
                () -> caseService.approve(c.getId(), new CaseApprovalRequest("ok")));
        assertTrue(same.getMessage().toLowerCase().contains("diferente"));

        // outro usuario sem perfil SUPERVISOR
        TestUsers.login(analyst, "ANALYST");
        assertThrows(AccessDeniedException.class, () -> caseService.approve(c.getId(), null));

        assertEquals("PENDING_APPROVAL", caseRepository.findById(c.getId()).orElseThrow().getStatus());
    }

    @Test
    void approvedCommunicateSetsDeadlineAndCreatesCosDraftOnly() {
        CaseResponse c = newCase(party(false, "LOW", false));
        TestUsers.login(supervisorA, "SUPERVISOR");
        caseService.decide(c.getId(), decision("COMMUNICATE", JUST));

        TestUsers.login(supervisorB, "SUPERVISOR");
        Instant before = Instant.now();
        CaseResponse approved = caseService.approve(c.getId(), new CaseApprovalRequest("de acordo"));

        assertEquals("COMMUNICATE", approved.getStatus());
        assertNotNull(approved.getApprovedAt());
        assertNotNull(approved.getApprovedById());
        LocalDate expected = BusinessCalendar.nationalOnly().nextBusinessDay(before);
        assertEquals(expected, approved.getCommunicationDeadline().atZone(BusinessCalendar.BRAZIL).toLocalDate());

        var cos = siscoafRepository.findByCaseEntityIdAndType(c.getId(), "COS");
        assertEquals(1, cos.size());
        assertEquals("DRAFT", cos.get(0).getStatus(), "COS nunca e enviada automaticamente");
        assertNull(cos.get(0).getSubmittedAt());
        assertEquals(approved.getCommunicationDeadline(), cos.get(0).getDeadlineAt());

        var audit = auditService.findByEntity("CASE", c.getId(), Pageable.unpaged()).getContent();
        assertTrue(audit.stream().anyMatch(l -> "CASE_DECIDED".equals(l.getAction())));
        var approvedLog = audit.stream().filter(l -> "CASE_APPROVED".equals(l.getAction())).findFirst().orElseThrow();
        assertNotNull(approvedLog.getOldValue());
        assertNotNull(approvedLog.getNewValue());
    }

    @Test
    void approvedArchiveOfSensitiveCaseIsArchived() {
        CaseResponse c = newCase(party(true, "HIGH", true));
        TestUsers.login(supervisorA, "SUPERVISOR");
        caseService.decide(c.getId(), decision("ARCHIVE", JUST));
        TestUsers.login(supervisorB, "SUPERVISOR");
        CaseResponse approved = caseService.approve(c.getId(), null);
        assertEquals("ARCHIVED", approved.getStatus());
        assertNotNull(approved.getClosedAt());
        assertTrue(siscoafRepository.findByCaseEntityIdAndType(c.getId(), "COS").isEmpty());
    }

    @Test
    void approveRequiresPendingApproval() {
        CaseResponse c = newCase(party(false, "LOW", false));
        TestUsers.login(supervisorA, "SUPERVISOR");
        assertThrows(BusinessException.class, () -> caseService.approve(c.getId(), null));
    }

    // ------------------------------------------------------------------ imutabilidade

    @Test
    void finalDecisionCannotBeChangedEscalatedOrReapproved() {
        CaseResponse c = newCase(party(false, "LOW", false));
        TestUsers.login(supervisorA, "SUPERVISOR");
        caseService.decide(c.getId(), decision("COMMUNICATE", JUST));
        TestUsers.login(supervisorB, "SUPERVISOR");
        caseService.approve(c.getId(), null);

        assertThrows(BusinessException.class, () -> caseService.decide(c.getId(), decision("ARCHIVE", JUST)));
        assertThrows(BusinessException.class,
                () -> caseService.escalate(c.getId(), new CaseEscalationRequest(JUST, null)));
        assertThrows(BusinessException.class, () -> caseService.approve(c.getId(), null));
    }

    @Test
    void casePendingApprovalCannotBeRedecided() {
        CaseResponse c = newCase(party(false, "LOW", false));
        caseService.decide(c.getId(), decision("COMMUNICATE", JUST));
        assertThrows(BusinessException.class, () -> caseService.decide(c.getId(), decision("ARCHIVE", JUST)));
        assertThrows(BusinessException.class,
                () -> caseService.escalate(c.getId(), new CaseEscalationRequest(JUST, null)));
    }

    // ------------------------------------------------------------------ escalonamento

    @Test
    void escalationRequiresJustificationIncrementsLevelAndIsNotFinal() {
        CaseResponse c = newCase(party(false, "LOW", false));
        assertThrows(BusinessException.class,
                () -> caseService.escalate(c.getId(), new CaseEscalationRequest("curta", null)));

        var target = userRepository.findByUsername(supervisorA).orElseThrow();
        CaseResponse e = caseService.escalate(c.getId(), new CaseEscalationRequest(JUST, target.getId()));
        assertEquals("ESCALATED", e.getStatus());
        assertEquals(1, e.getEscalationLevel());
        assertNull(e.getDecidedAt());

        var entity = caseRepository.findById(c.getId()).orElseThrow();
        assertEquals(target.getId(), entity.getEscalatedTo().getId());
        assertNotNull(entity.getEscalatedAt());

        // case escalado ainda pode receber decisao final
        assertEquals("ARCHIVED", caseService.decide(c.getId(), decision("ARCHIVE", JUST)).getStatus());
    }
}
