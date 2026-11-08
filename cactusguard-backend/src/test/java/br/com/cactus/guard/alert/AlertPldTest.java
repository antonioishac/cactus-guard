package br.com.cactus.guard.alert;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.alert.repository.AlertTransactionRepository;
import br.com.cactus.guard.alert.service.AlertCreationService;
import br.com.cactus.guard.alert.service.AlertCreationService.AlertDraft;
import br.com.cactus.guard.alert.service.AlertService;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.common.calendar.BusinessCalendar;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.support.TestUsers;
import br.com.cactus.guard.transaction.entity.Transaction;
import br.com.cactus.guard.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AlertPldTest {

    @Autowired private AlertCreationService creation;
    @Autowired private AlertService alertService;
    @Autowired private AlertRepository alertRepository;
    @Autowired private AlertTransactionRepository alertTransactionRepository;
    @Autowired private PartyRepository partyRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private UserRepository userRepository;

    @AfterEach
    void cleanup() {
        TestUsers.logout();
    }

    private Party party() {
        return partyRepository.save(Party.builder()
                .type("PF").document(UUID.randomUUID().toString().replace("-", "").substring(0, 11))
                .name("Alert PLD").status("ACTIVE").build());
    }

    private Transaction txn(Party p, Instant when) {
        return transactionRepository.save(Transaction.builder().party(p).type("TED")
                .amount(new BigDecimal("100")).currency("BRL").status("COMPLETED").transactionDate(when).build());
    }

    @Test
    void occurredAtIsOldestTransactionAndDeadlineIs45DaysLater() {
        Party p = party();
        Instant oldest = Instant.now().minus(Duration.ofDays(12));
        Alert a = creation.create(new AlertDraft(p, null, "TRANSACTIONAL", "HIGH", "t", "d", "STRUCTURING",
                null, null, null, null,
                List.of(txn(p, Instant.now().minus(Duration.ofDays(2))), txn(p, oldest), txn(p, Instant.now()))));

        assertEquals(oldest, a.getOccurredAt());
        assertEquals(BusinessCalendar.plusCalendarDays(oldest, 45), a.getSelectionDeadline());
        assertEquals(3, alertTransactionRepository.findByAlertId(a.getId()).size());
    }

    @Test
    void occurredAtFallsBackToNowWithoutTransactions() {
        Instant before = Instant.now();
        Alert a = creation.create(new AlertDraft(party(), null, "SCREENING", "HIGH", "t", "d", "PEP_MATCH",
                null, null, null, null, List.of()));
        assertFalse(a.getOccurredAt().isBefore(before.minusSeconds(1)));
        assertEquals(BusinessCalendar.plusCalendarDays(a.getOccurredAt(), 45), a.getSelectionDeadline());
        assertEquals("OPEN", a.getStatus());
    }

    @Test
    void dedupKeyPreventsDuplicateAlerts() {
        Party p = party();
        String key = "TEST:" + p.getId();
        AlertDraft draft = new AlertDraft(p, null, "X", "LOW", "t", "d", "TEST", key, null, null,
                Map.of("k", "v"), List.of());
        assertTrue(creation.createIfAbsent(draft).isPresent());
        assertTrue(creation.createIfAbsent(draft).isEmpty());
        assertEquals(1, alertRepository.findExistingDedupKeys(List.of(key)).size());
    }

    @Test
    void severityEscalationCapsAtCritical() {
        assertEquals("MEDIUM", AlertCreationService.escalate("LOW"));
        assertEquals("HIGH", AlertCreationService.escalate("MEDIUM"));
        assertEquals("CRITICAL", AlertCreationService.escalate("HIGH"));
        assertEquals("CRITICAL", AlertCreationService.escalate("CRITICAL"));
    }

    @Test
    void falsePositiveRequiresJustificationAndRecordsWhoAndWhy() {
        String user = TestUsers.unique("analyst");
        var u = TestUsers.ensure(userRepository, user);
        TestUsers.login(user, "ANALYST");
        Alert a = creation.create(new AlertDraft(party(), null, "X", "LOW", "t", "d", "TEST",
                null, null, null, null, List.of()));

        assertThrows(BusinessException.class, () -> alertService.markFalsePositive(a.getId(), "OTHER", "curto"));
        assertThrows(BusinessException.class, () -> alertService.markFalsePositive(a.getId(), " ", "Justificativa suficientemente detalhada"));

        var resp = alertService.markFalsePositive(a.getId(), "KNOWN_CUSTOMER_PATTERN",
                "Cliente com padrao sazonal documentado no dossie de KYC");
        assertEquals("FALSE_POSITIVE", resp.getStatus());

        Alert saved = alertRepository.findById(a.getId()).orElseThrow();
        assertEquals("KNOWN_CUSTOMER_PATTERN", saved.getFalsePositiveReason());
        assertEquals("FALSE_POSITIVE", saved.getClosureReason());
        assertNotNull(saved.getClosedAt());
        assertEquals(u.getId(), saved.getClosedBy().getId());
        assertEquals("Cliente com padrao sazonal documentado no dossie de KYC", saved.getClosureJustification());

        // nao fecha duas vezes
        assertThrows(BusinessException.class, () -> alertService.markFalsePositive(a.getId(), "OTHER",
                "Segunda tentativa de encerrar o mesmo alerta"));
    }
}
