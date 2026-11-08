package br.com.cactus.guard.alert;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.alert.service.AlertCreationService;
import br.com.cactus.guard.alert.service.AlertCreationService.AlertDraft;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.case_.dto.CaseDecisionRequest;
import br.com.cactus.guard.case_.service.CaseService;
import br.com.cactus.guard.common.event.CasePendingApprovalEvent;
import br.com.cactus.guard.common.event.CriticalAlertCreatedEvent;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Eventos de dominio publicados para a trilha de push (dentro da transacao; listener AFTER_COMMIT). */
@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class EventPublicationTest {

    private static final String JUST = "Analise concluida com base nos documentos e na movimentacao";

    @Autowired private ApplicationEvents events;
    @Autowired private AlertCreationService alertCreation;
    @Autowired private AlertRepository alertRepository;
    @Autowired private CaseService caseService;
    @Autowired private PartyRepository partyRepository;
    @Autowired private UserRepository userRepository;

    @AfterEach
    void logout() {
        TestUsers.logout();
    }

    private Party party(boolean pep) {
        return partyRepository.save(Party.builder().type("PF")
                .document(UUID.randomUUID().toString().replace("-", "").substring(0, 11))
                .name("Parte Evento").status("ACTIVE").pep(pep).riskLevel(pep ? "HIGH" : "LOW").build());
    }

    private AlertDraft draft(Party p, String severity, String dedup) {
        return new AlertDraft(p, null, "TRANSACTIONAL", severity, "t", "d", "RULE_EVT", dedup,
                null, Instant.now(), null, List.of());
    }

    @Test
    void criticalAlertPublishesExactlyOneEventAndNonCriticalPublishesNone() {
        Party p = party(false);
        Alert critical = alertCreation.create(draft(p, "CRITICAL", null));
        alertCreation.create(draft(p, "HIGH", null));

        var published = events.stream(CriticalAlertCreatedEvent.class).toList();
        assertEquals(1, published.size());
        assertEquals(critical.getId(), published.get(0).alertId());
    }

    @Test
    void dedupHitDoesNotPublishAgain() {
        Party p = party(false);
        String key = "EVT:" + UUID.randomUUID();
        assertTrue(alertCreation.createIfAbsent(draft(p, "CRITICAL", key)).isPresent());
        assertTrue(alertCreation.createIfAbsent(draft(p, "CRITICAL", key)).isEmpty());
        assertEquals(1, events.stream(CriticalAlertCreatedEvent.class).count());
    }

    @Test
    void pendingApprovalPublishesEventWithDeciderAndArchiveDoesNot() {
        User analyst = TestUsers.ensure(userRepository, TestUsers.unique("analyst"));
        TestUsers.login(analyst.getUsername(), "ANALYST");

        Alert sensitive = alertRepository.save(Alert.builder().party(party(true)).type("T").severity("HIGH")
                .status("OPEN").title("a").createdAt(Instant.now()).build());
        var c1 = caseService.createFromAlert(sensitive.getId());
        CaseDecisionRequest req = new CaseDecisionRequest();
        req.setDecision("COMMUNICATE");
        req.setJustification(JUST);
        caseService.decide(c1.getId(), req);

        Alert plain = alertRepository.save(Alert.builder().party(party(false)).type("T").severity("LOW")
                .status("OPEN").title("a").createdAt(Instant.now()).build());
        var c2 = caseService.createFromAlert(plain.getId());
        CaseDecisionRequest archive = new CaseDecisionRequest();
        archive.setDecision("ARCHIVE");
        archive.setJustification(JUST);
        assertEquals("ARCHIVED", caseService.decide(c2.getId(), archive).getStatus());

        var published = events.stream(CasePendingApprovalEvent.class).toList();
        assertEquals(1, published.size());
        assertEquals(c1.getId(), published.get(0).caseId());
        assertEquals(analyst.getId(), published.get(0).decidedById());
    }
}
