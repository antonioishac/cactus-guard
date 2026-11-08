package br.com.cactus.guard.alert;

import br.com.cactus.guard.alert.dto.AlertDetailResponse;
import br.com.cactus.guard.alert.dto.AlertResponse;
import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.entity.AlertTransaction;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.alert.repository.AlertSpecifications;
import br.com.cactus.guard.alert.repository.AlertTransactionRepository;
import br.com.cactus.guard.alert.service.AlertService;
import br.com.cactus.guard.audit.repository.AuditLogRepository;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.UserRepository;
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
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AlertAlignmentTest {

    private static final String JUST = "Cliente com padrao fora do perfil, escalando para nivel superior";

    @Autowired private AlertService alertService;
    @Autowired private AlertRepository alertRepository;
    @Autowired private AlertTransactionRepository alertTransactionRepository;
    @Autowired private PartyRepository partyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private User analyst;

    @BeforeEach
    void login() {
        analyst = TestUsers.ensure(userRepository, TestUsers.unique("analyst"));
        TestUsers.login(analyst.getUsername(), "ANALYST");
    }

    @AfterEach
    void logout() {
        TestUsers.logout();
    }

    private Party party(String risk) {
        return partyRepository.save(Party.builder().type("PF")
                .document(UUID.randomUUID().toString().replace("-", "").substring(0, 11))
                .name("Parte Alerta").status("ACTIVE").pep(true).riskLevel(risk).build());
    }

    private Alert alert(Party p, String type, String severity, String status, String ruleCode, Instant createdAt) {
        return alertRepository.save(Alert.builder().party(p).type(type).severity(severity).status(status)
                .title("t").ruleCode(ruleCode).createdAt(createdAt).build());
    }

    private List<UUID> ids(AlertSpecifications.Filter f) {
        return alertService.findAll(f, PageRequest.of(0, 50)).map(AlertResponse::getId).getContent();
    }

    @Test
    void filtersCombineStatusSeverityTypeAssigneeRuleAndParty() {
        Party p = party("HIGH");
        String rule = TestUsers.unique("R");
        Alert open = alert(p, "TRANSACTIONAL", "HIGH", "OPEN", rule, Instant.now());
        Alert esc = alert(p, "TRANSACTIONAL", "LOW", "ESCALATED", rule, Instant.now());
        Alert other = alert(p, "SCREENING", "HIGH", "OPEN", "OUTRA", Instant.now());
        esc.setAssignedTo(analyst);
        alertRepository.save(esc);

        var byStatuses = new AlertSpecifications.Filter(List.of("OPEN", "ESCALATED"), null, null, null, null, null, p.getId(), rule);
        assertEquals(2, ids(byStatuses).size());
        assertTrue(ids(byStatuses).containsAll(List.of(open.getId(), esc.getId())));

        assertEquals(List.of(open.getId()), ids(new AlertSpecifications.Filter(null, List.of("HIGH"), "TRANSACTIONAL", null, null, null, p.getId(), rule)));
        assertEquals(List.of(esc.getId()), ids(new AlertSpecifications.Filter(null, null, null, analyst.getId(), null, null, p.getId(), null)));
        assertEquals(List.of(other.getId()), ids(new AlertSpecifications.Filter(null, null, "SCREENING", null, null, null, p.getId(), null)));
    }

    @Test
    void periodFilterUsesCreatedAt() {
        Party p = party("LOW");
        Alert old = alert(p, "TRANSACTIONAL", "LOW", "OPEN", "R", Instant.now());
        Alert recent = alert(p, "TRANSACTIONAL", "LOW", "OPEN", "R", Instant.now());
        // created_at e gerado em @PrePersist: recua a data direto no banco
        jdbc.update("update alerts set created_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(40))), old.getId());
        jdbc.update("update alerts set created_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(1))), recent.getId());
        var f = new AlertSpecifications.Filter(null, null, null, null, Instant.now().minus(Duration.ofDays(7)), null, p.getId(), null);
        assertEquals(List.of(recent.getId()), ids(f));
        var g = new AlertSpecifications.Filter(null, null, null, null, null, Instant.now().minus(Duration.ofDays(7)), p.getId(), null);
        assertEquals(List.of(old.getId()), ids(g));
    }

    @Test
    void listExposesMaskedPartyDocument() {
        Party p = party("LOW");
        alert(p, "TRANSACTIONAL", "LOW", "OPEN", "R", Instant.now());
        AlertResponse r = alertService.findAll(new AlertSpecifications.Filter(null, null, null, null, null, null, p.getId(), null),
                PageRequest.of(0, 5)).getContent().get(0);
        assertTrue(r.getPartyDocument().startsWith("*"));
        assertFalse(r.getPartyDocument().contains(p.getDocument().substring(0, 6)));
    }

    @Test
    void escalateRequiresJustificationRecordsHistoryAndAudit() {
        Alert a = alert(party("LOW"), "TRANSACTIONAL", "HIGH", "OPEN", "R", Instant.now());
        assertThrows(BusinessException.class, () -> alertService.escalate(a.getId(), "curta"));
        assertThrows(BusinessException.class, () -> alertService.escalate(a.getId(), null));

        AlertResponse r = alertService.escalate(a.getId(), JUST);
        assertEquals("ESCALATED", r.getStatus());
        assertThrows(BusinessException.class, () -> alertService.escalate(a.getId(), JUST));

        AlertDetailResponse d = alertService.findDetail(a.getId());
        assertTrue(d.history().stream().anyMatch(h -> "ESCALATE".equals(h.action()) && JUST.equals(h.justification())
                && h.actorName().equals(analyst.getUsername())));
        assertTrue(auditLogRepository.findByEntityTypeAndEntityId("ALERT", a.getId(), Pageable.unpaged())
                .stream().anyMatch(l -> "ALERT_ESCALATED".equals(l.getAction())));
    }

    @Test
    void commentsAreStoredWithAuthorAuditedWithoutContentAndDoNotChangeStatus() {
        Alert a = alert(party("LOW"), "TRANSACTIONAL", "HIGH", "OPEN", "R", Instant.now());
        assertThrows(BusinessException.class, () -> alertService.addComment(a.getId(), "  "));

        var c1 = alertService.addComment(a.getId(), "Contato com a agencia confirmou o deposito");
        alertService.addComment(a.getId(), "Segundo comentario");
        var list = alertService.listComments(a.getId());
        assertEquals(2, list.size());
        assertEquals("Contato com a agencia confirmou o deposito", list.get(0).text());
        assertEquals(analyst.getUsername(), list.get(0).authorName());
        assertEquals(c1.id(), list.get(0).id());
        assertNotNull(list.get(0).createdAt());
        assertEquals("OPEN", alertService.findById(a.getId()).getStatus());

        var audit = auditLogRepository.findByEntityTypeAndEntityId("ALERT", a.getId(), Pageable.unpaged()).getContent();
        assertEquals(2, audit.stream().filter(l -> "ALERT_COMMENT_ADDED".equals(l.getAction())).count());
        assertTrue(audit.stream().noneMatch(l -> String.valueOf(l.getNewValue()).contains("agencia")));
        assertThrows(IllegalArgumentException.class, () -> alertService.listComments(UUID.randomUUID()));
    }

    @Test
    void closedAlertCannotBeEscalated() {
        Alert a = alert(party("LOW"), "TRANSACTIONAL", "HIGH", "FALSE_POSITIVE", "R", Instant.now());
        assertThrows(BusinessException.class, () -> alertService.escalate(a.getId(), JUST));
    }

    @Test
    void detailHasEvidenceTransactionsHistoryRuleAndMaskedParty() {
        Party p = party("HIGH");
        Alert a = alert(p, "TRANSACTIONAL", "HIGH", "OPEN", "STRUCTURING", Instant.now());
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("count", Map.of("label", "Quantidade", "threshold", 3, "observed", 5));
        evidence.put("windowDays", 7);
        a.setEvidence(evidence);
        a.setRuleVersion(2);
        alertRepository.save(a);
        Transaction t = transactionRepository.save(Transaction.builder().party(p).type("DEPOSIT")
                .amount(new BigDecimal("4900.00")).currency("BRL").transactionDate(Instant.now())
                .status("COMPLETED").direction("IN").counterpartyDocument("12345678901").build());
        alertTransactionRepository.save(AlertTransaction.builder().alert(a).transaction(t).build());
        alertService.assume(a.getId());

        AlertDetailResponse d = alertService.findDetail(a.getId());

        assertEquals(2, d.evidence().size());
        var count = d.evidence().stream().filter(e -> e.parameter().equals("count")).findFirst().orElseThrow();
        assertEquals(3, count.threshold());
        assertEquals(5, count.observed());
        assertEquals(List.of(t.getId()), d.triggeringTransactionIds());
        assertEquals(1, d.transactions().size());
        assertTrue(d.transactions().get(0).counterpartyDocument().startsWith("*"));
        assertTrue(d.history().stream().anyMatch(h -> "ASSUME".equals(h.action())));
        assertEquals("STRUCTURING", d.rule().code());
        assertEquals(2, d.rule().version());
        assertTrue(d.party().pep());
        assertEquals("HIGH", d.party().riskLevel());
        assertTrue(d.party().document().startsWith("*"));
        assertTrue(d.partyDocument().startsWith("*"));
    }
}
