package br.com.cactus.guard.case_;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.entity.AlertTransaction;
import br.com.cactus.guard.alert.repository.AlertHistoryRepository;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.alert.repository.AlertTransactionRepository;
import br.com.cactus.guard.audit.entity.AuditLog;
import br.com.cactus.guard.audit.repository.AuditLogRepository;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.case_.dto.CaseApprovalRequest;
import br.com.cactus.guard.case_.dto.CaseDecisionRequest;
import br.com.cactus.guard.case_.dto.CaseDossierResponse;
import br.com.cactus.guard.case_.dto.CaseResponse;
import br.com.cactus.guard.case_.entity.Case;
import br.com.cactus.guard.case_.repository.CaseRepository;
import br.com.cactus.guard.case_.repository.CaseSpecifications;
import br.com.cactus.guard.case_.service.CaseDossierService;
import br.com.cactus.guard.case_.service.CaseEvidenceService;
import br.com.cactus.guard.case_.service.CaseNoteService;
import br.com.cactus.guard.case_.service.CaseService;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.support.TestUsers;
import br.com.cactus.guard.transaction.entity.Transaction;
import br.com.cactus.guard.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Contratos de alinhamento backend/frontend para cases: numero, filtros, SLA, rejeicao, notas, evidencias, dossie. */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "cases.evidence.dir=target/test-case-evidences")
class CaseAlignmentTest {

    private static final String JUST = "Analise concluida com base nos documentos e na movimentacao";
    private static final byte[] PDF = "%PDF-1.4\nconteudo de teste da evidencia\n".getBytes(StandardCharsets.UTF_8);

    @Autowired private CaseService caseService;
    @Autowired private CaseRepository caseRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private AlertHistoryRepository alertHistoryRepository;
    @Autowired private AlertTransactionRepository alertTransactionRepository;
    @Autowired private PartyRepository partyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private CaseNoteService noteService;
    @Autowired private CaseEvidenceService evidenceService;
    @Autowired private CaseDossierService dossierService;

    private String analyst;
    private String supA;
    private String supB;

    @BeforeEach
    void users() {
        analyst = TestUsers.unique("analyst");
        supA = TestUsers.unique("supA");
        supB = TestUsers.unique("supB");
        for (String u : new String[]{analyst, supA, supB}) {
            TestUsers.ensure(userRepository, u);
        }
        TestUsers.login(analyst, "ANALYST");
    }

    @AfterEach
    void cleanup() {
        TestUsers.logout();
    }

    private Party party(boolean pep, String risk, boolean edd) {
        return partyRepository.save(Party.builder().type("PF")
                .document(UUID.randomUUID().toString().replace("-", "").substring(0, 11))
                .name("Parte Alinhamento").status("ACTIVE").pep(pep).riskLevel(risk).eddRequired(edd).build());
    }

    private Alert alert(Party p) {
        return alertRepository.save(Alert.builder().party(p).type("TRANSACTIONAL").severity("HIGH").status("OPEN")
                .title("alerta").ruleCode("STRUCTURING").createdAt(Instant.now()).build());
    }

    private CaseResponse newCase(Party p) {
        return caseService.createFromAlert(alert(p).getId());
    }

    private CaseDecisionRequest decision(String d) {
        CaseDecisionRequest r = new CaseDecisionRequest();
        r.setDecision(d);
        r.setJustification(JUST);
        return r;
    }

    private CaseResponse pendingApproval(Party p) {
        CaseResponse c = newCase(p);
        return caseService.decide(c.getId(), decision("COMMUNICATE"));
    }

    // ------------------------------------------------------------------ numero e abertura

    @Test
    void caseNumberIsSequentialPerYearAndReadable() {
        CaseResponse a = newCase(party(false, "LOW", false));
        CaseResponse b = newCase(party(false, "LOW", false));
        String year = String.valueOf(java.time.Year.now(br.com.cactus.guard.common.calendar.BusinessCalendar.BRAZIL).getValue());
        assertTrue(a.getCaseNumber().matches("CG-" + year + "-\\d{6}"), a.getCaseNumber());
        assertEquals(Long.parseLong(a.getCaseNumber().substring(8)) + 1, Long.parseLong(b.getCaseNumber().substring(8)));
    }

    @Test
    void openingWithJustificationRecordsItInAlertHistoryAndAudit() {
        Alert a = alert(party(false, "LOW", false));
        CaseResponse c = caseService.createFromAlert(a.getId(), JUST);
        var history = alertHistoryRepository.findByAlertIdOrderByCreatedAtDesc(a.getId());
        assertTrue(history.stream().anyMatch(h -> "CASE_OPENED".equals(h.getAction()) && JUST.equals(h.getComment())));
        assertTrue(auditLogRepository.findByEntityTypeAndEntityId("CASE", c.getId(), Pageable.unpaged())
                .stream().anyMatch(l -> "CASE_CREATED".equals(l.getAction())
                        && JUST.equals(l.getNewValue().get("selectionJustification"))));
    }

    @Test
    void openingWithShortJustificationIsRejected() {
        Alert a = alert(party(false, "LOW", false));
        assertThrows(BusinessException.class, () -> caseService.createFromAlert(a.getId(), "curta"));
    }

    // ------------------------------------------------------------------ filtros e SLA

    @Test
    void responseExposesDecisionMakerSlaAndDeadlines() {
        CaseResponse c = pendingApproval(party(true, "HIGH", false));
        assertNotNull(c.getDecidedById());
        assertEquals("ON_TIME", c.getSlaStatus());
        assertNotNull(c.getAnalysisDeadline());
        assertEquals("HIGH", c.getPartyRiskLevel());
    }

    @Test
    void slaFilterClassifiesByRegulatoryDeadline() {
        Party p = party(false, "MEDIUM", false);
        CaseResponse onTime = newCase(p);
        CaseResponse soon = newCase(p);
        CaseResponse overdue = newCase(p);
        setAnalysisDeadline(soon.getId(), Instant.now().plus(Duration.ofDays(2)));
        setAnalysisDeadline(overdue.getId(), Instant.now().minus(Duration.ofHours(1)));

        assertEquals(List.of(overdue.getId()), idsBySla("OVERDUE", p.getId()));
        assertEquals(List.of(soon.getId()), idsBySla("DUE_SOON", p.getId()));
        assertEquals(List.of(onTime.getId()), idsBySla("ON_TIME", p.getId()));
        assertEquals("OVERDUE", caseService.findById(overdue.getId()).getSlaStatus());
        assertThrows(BusinessException.class, () -> idsBySla("XYZ", p.getId()));
    }

    @Test
    void communicationDeadlineDrivesSlaOfApprovedCommunicate() {
        Party p = party(false, "LOW", false);
        CaseResponse c = newCase(p);
        caseService.decide(c.getId(), decision("COMMUNICATE"));
        TestUsers.login(supA, "SUPERVISOR");
        caseService.approve(c.getId(), new CaseApprovalRequest("ok"));
        Case entity = caseRepository.findById(c.getId()).orElseThrow();
        entity.setCommunicationDeadline(Instant.now().minus(Duration.ofHours(2)));
        caseRepository.save(entity);
        assertEquals("OVERDUE", caseService.findById(c.getId()).getSlaStatus());
        assertEquals(List.of(c.getId()), idsBySla("OVERDUE", p.getId()));
    }

    @Test
    void multiStatusRiskAndEddFilters() {
        Party high = party(false, "HIGH", true);
        CaseResponse open = newCase(high);
        CaseResponse esc = newCase(high);
        caseService.escalate(esc.getId(), new br.com.cactus.guard.case_.dto.CaseEscalationRequest(JUST, null));
        newCase(party(false, "LOW", false));

        var f = new CaseSpecifications.Filter(List.of("OPEN", "ESCALATED"), null, null, "HIGH", true, null, null, high.getId());
        var page = caseService.findAll(f, PageRequest.of(0, 10));
        assertEquals(2, page.getTotalElements());
        assertTrue(page.getContent().stream().allMatch(r -> r.isEdd() && "HIGH".equals(r.getPartyRiskLevel())));

        var onlyEsc = new CaseSpecifications.Filter(List.of("ESCALATED"), null, null, null, null, null, null, high.getId());
        assertEquals(List.of(esc.getId()), caseService.findAll(onlyEsc, PageRequest.of(0, 10)).map(CaseResponse::getId).getContent());
        assertNotEquals(open.getId(), esc.getId());
    }

    private List<UUID> idsBySla(String sla, UUID partyId) {
        var f = new CaseSpecifications.Filter(null, null, sla, null, null, null, null, partyId);
        return caseService.findAll(f, PageRequest.of(0, 20)).map(CaseResponse::getId).getContent();
    }

    private void setAnalysisDeadline(UUID caseId, Instant deadline) {
        Case c = caseRepository.findById(caseId).orElseThrow();
        c.setAnalysisDeadline(deadline);
        caseRepository.save(c);
    }

    // ------------------------------------------------------------------ rejeicao da aprovacao

    @Test
    void rejectApprovalReturnsCaseToAnalysisKeepingAuditTrail() {
        CaseResponse pending = pendingApproval(party(false, "LOW", false));
        assertEquals("PENDING_APPROVAL", pending.getStatus());

        TestUsers.login(supA, "SUPERVISOR");
        String rejection = "Faltam documentos que comprovem a origem dos recursos declarados";
        CaseResponse back = caseService.rejectApproval(pending.getId(), rejection);

        assertEquals("IN_ANALYSIS", back.getStatus());
        assertNull(back.getDecision());
        assertNull(back.getDecidedById());
        assertNull(back.getDecidedAt());

        List<AuditLog> trail = auditLogRepository.findByEntityTypeAndEntityId("CASE", pending.getId(), Pageable.unpaged()).getContent();
        AuditLog rej = trail.stream().filter(l -> "CASE_APPROVAL_REJECTED".equals(l.getAction())).findFirst().orElseThrow();
        assertEquals(rejection, rej.getNewValue().get("rejectionJustification"));
        assertEquals("COMMUNICATE", rej.getNewValue().get("rejectedDecision"));
        assertEquals(JUST, rej.getOldValue().get("justification"));

        // o case voltou a aceitar nova decisao
        TestUsers.login(analyst, "ANALYST");
        assertEquals("ARCHIVED", caseService.decide(pending.getId(), decision("ARCHIVE")).getStatus());
    }

    @Test
    void rejectApprovalByTheDeciderIsBlockedAndJustificationIsMandatory() {
        TestUsers.login(supA, "SUPERVISOR");
        CaseResponse c = newCase(party(false, "LOW", false));
        CaseResponse pending = caseService.decide(c.getId(), decision("COMMUNICATE"));
        assertThrows(BusinessException.class,
                () -> caseService.rejectApproval(pending.getId(), "Justificativa suficientemente longa aqui"));
        TestUsers.login(supB, "SUPERVISOR");
        assertThrows(BusinessException.class, () -> caseService.rejectApproval(pending.getId(), "curta"));
        assertEquals("PENDING_APPROVAL", caseService.findById(pending.getId()).getStatus());
    }

    @Test
    void rejectApprovalRequiresSupervisorAndPendingStatus() {
        CaseResponse open = newCase(party(false, "LOW", false));
        assertThrows(AccessDeniedException.class,
                () -> caseService.rejectApproval(open.getId(), "Justificativa suficientemente longa aqui"));
        TestUsers.login(supA, "SUPERVISOR");
        assertThrows(BusinessException.class,
                () -> caseService.rejectApproval(open.getId(), "Justificativa suficientemente longa aqui"));
    }

    // ------------------------------------------------------------------ notas

    @Test
    void notesAreAppendedAndListedInOrderWithoutContentInAudit() {
        CaseResponse c = newCase(party(false, "LOW", false));
        noteService.add(c.getId(), "Primeira nota de analise");
        noteService.add(c.getId(), "Segunda nota de analise");
        var notes = noteService.list(c.getId());
        assertEquals(2, notes.size());
        assertEquals("Primeira nota de analise", notes.get(0).text());
        assertEquals(analyst, notes.get(0).authorName());
        assertTrue(auditLogRepository.findByEntityTypeAndEntityId("CASE", c.getId(), Pageable.unpaged()).stream()
                .filter(l -> "CASE_NOTE_ADDED".equals(l.getAction()))
                .noneMatch(l -> String.valueOf(l.getNewValue()).contains("nota de analise")));
        assertThrows(BusinessException.class, () -> noteService.add(c.getId(), "   "));
    }

    // ------------------------------------------------------------------ evidencias

    @Test
    void evidenceUploadComputesSha256AndDownloadRoundTrips() {
        CaseResponse c = newCase(party(false, "LOW", false));
        var ev = evidenceService.upload(c.getId(), "../../extrato 01.pdf", "application/pdf", PDF, "extrato");
        assertEquals(64, ev.sha256().length());
        assertEquals(PDF.length, ev.sizeBytes());
        assertFalse(ev.fileName().contains("/"));
        assertEquals(1, evidenceService.list(c.getId()).size());

        var file = evidenceService.download(c.getId(), ev.id());
        assertArrayEquals(PDF, file.content());
        assertEquals("application/pdf", file.contentType());

        assertThrows(BusinessException.class,
                () -> evidenceService.upload(c.getId(), "outro.pdf", "application/pdf", PDF, null)); // mesmo sha256
    }

    @Test
    void evidenceUploadValidatesTypeSignatureAndSize() {
        CaseResponse c = newCase(party(false, "LOW", false));
        assertThrows(BusinessException.class, () -> evidenceService.upload(c.getId(), "a.exe", "application/x-msdownload", PDF, null));
        assertThrows(BusinessException.class, () -> evidenceService.upload(c.getId(), "a.pdf", "application/pdf",
                "nao e pdf".getBytes(StandardCharsets.UTF_8), null));
        assertThrows(BusinessException.class, () -> evidenceService.upload(c.getId(), "a.png", "application/pdf", PDF, null));
        assertThrows(BusinessException.class, () -> evidenceService.upload(c.getId(), "a.pdf", "application/pdf", new byte[0], null));
    }

    @Test
    void evidenceDownloadFromAnotherCaseIsNotFound() {
        CaseResponse c1 = newCase(party(false, "LOW", false));
        CaseResponse c2 = newCase(party(false, "LOW", false));
        var ev = evidenceService.upload(c1.getId(), "e.pdf", "application/pdf", PDF, null);
        assertThrows(BusinessException.class, () -> evidenceService.download(c2.getId(), ev.id()));
    }

    // ------------------------------------------------------------------ dossie

    @Test
    void dossierConsolidatesEverythingWithMaskedPii() {
        Party p = party(true, "HIGH", true);
        Alert a = alert(p);
        Transaction t = transactionRepository.save(Transaction.builder().party(p).type("TRANSFER")
                .amount(new BigDecimal("9500.00")).currency("BRL").transactionDate(Instant.now().minus(Duration.ofDays(2)))
                .status("COMPLETED").direction("OUT").counterpartyDocument("98765432100").build());
        alertTransactionRepository.save(AlertTransaction.builder().alert(a).transaction(t).build());
        CaseResponse c = caseService.createFromAlert(a.getId(), JUST);
        noteService.add(c.getId(), "nota do dossie");
        evidenceService.upload(c.getId(), "doc.pdf", "application/pdf", PDF, null);

        CaseDossierResponse d = dossierService.dossier(c.getId());

        assertEquals(c.getId(), d.caseInfo().getId());
        assertNotNull(d.caseInfo().getCaseNumber());
        assertEquals(p.getId(), d.party().id());
        assertTrue(d.party().document().startsWith("*"), "documento deve vir mascarado");
        assertFalse(d.party().document().contains(p.getDocument().substring(0, 5)));
        assertTrue(d.pep().pep());
        assertEquals(1, d.alertTransactions().size());
        assertTrue(d.alertTransactions().get(0).counterpartyDocument().startsWith("*"));
        assertEquals(1, d.periodTransactions().size());
        assertEquals(1, d.evidences().size());
        assertEquals(1, d.notes().size());
        assertTrue(d.auditTrail().stream().anyMatch(i -> "CASE_CREATED".equals(i.action())));
        assertNotNull(d.alert());
    }
}
