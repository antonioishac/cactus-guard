package br.com.cactus.guard.party;

import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.party.dto.PepRequest;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.party.service.PartyPepService;
import br.com.cactus.guard.risk.repository.RiskHistoryRepository;
import br.com.cactus.guard.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PepGovernanceTest {

    private static final String JUST = "Cargo confirmado em fonte oficial do Diario Oficial da Uniao";

    @Autowired private PartyPepService pepService;
    @Autowired private PartyRepository partyRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditService auditService;
    @Autowired private RiskHistoryRepository riskHistoryRepository;

    private String analyst;
    private String supervisor;

    @BeforeEach
    void users() {
        analyst = TestUsers.unique("analyst");
        supervisor = TestUsers.unique("sup");
        TestUsers.ensure(userRepository, analyst);
        TestUsers.ensure(userRepository, supervisor);
        TestUsers.login(analyst, "ANALYST");
    }

    @AfterEach
    void cleanup() {
        TestUsers.logout();
    }

    private Party party() {
        return partyRepository.save(Party.builder().type("PF")
                .document(UUID.randomUUID().toString().replace("-", "").substring(0, 11))
                .name("Pep Test").status("ACTIVE").riskLevel("LOW").build());
    }

    @Test
    void expiryIsEndDatePlusFiveYears() {
        assertEquals(LocalDate.of(2031, 3, 15), PartyPepService.expiryDate(LocalDate.of(2026, 3, 15)));
        assertEquals(LocalDate.of(2029, 2, 28), PartyPepService.expiryDate(LocalDate.of(2024, 2, 29)));
        assertNull(PartyPepService.expiryDate(null));
    }

    @Test
    void markingPepForcesEddAndAtLeastHighRisk() {
        Party p = party();
        var r = pepService.markPep(p.getId(), new PepRequest("NATIONAL", "Deputado Federal",
                LocalDate.of(2019, 2, 1), LocalDate.now().minusYears(1), false, JUST));

        assertTrue(r.pep());
        assertEquals("ACTIVE", r.pepStatus());
        assertEquals(LocalDate.now().minusYears(1).plusYears(5), r.expiryDate());
        assertTrue(r.eddRequired());
        assertFalse(r.approved(), "sem aprovacao da alta administracao ate aprovar");
        assertTrue(java.util.List.of("HIGH", "CRITICAL").contains(r.riskLevel()));

        Party saved = partyRepository.findById(p.getId()).orElseThrow();
        assertTrue(saved.isEddRequired());
        assertTrue(saved.getRiskScore().compareTo(new java.math.BigDecimal("75")) >= 0);
        assertNotNull(saved.getRiskCalculatedAt());
        assertNotNull(saved.getNextReviewDate());
        assertTrue(riskHistoryRepository.findByPartyIdOrderByCreatedAtDesc(p.getId(), Pageable.unpaged())
                .getContent().stream().anyMatch(h -> h.getReason().contains("PEP")));
        var audit = auditService.findByEntity("PARTY", p.getId(), Pageable.unpaged()).getContent();
        assertTrue(audit.stream().anyMatch(a -> "PEP_MARKED".equals(a.getAction())));
    }

    @Test
    void markingPepRequiresJustificationAndConsistentDates() {
        Party p = party();
        assertThrows(BusinessException.class, () -> pepService.markPep(p.getId(),
                new PepRequest("NATIONAL", "x", null, null, false, "curta")));
        assertThrows(BusinessException.class, () -> pepService.markPep(p.getId(),
                new PepRequest("NATIONAL", "x", LocalDate.of(2020, 1, 1), LocalDate.of(2019, 1, 1), false, JUST)));
    }

    @Test
    void alreadyExpiredPepIsStoredAsExpiredAndRelatedStaysRelated() {
        Party old = party();
        var expired = pepService.markPep(old.getId(), new PepRequest("NATIONAL", "Ex-ministro",
                LocalDate.of(1990, 1, 1), LocalDate.of(2000, 1, 1), false, JUST));
        assertEquals("EXPIRED", expired.pepStatus());

        Party rel = party();
        var related = pepService.markPep(rel.getId(), new PepRequest("NATIONAL", "Familiar", null, null, true, JUST));
        assertEquals("RELATED", related.pepStatus());
        assertNull(related.expiryDate(), "ainda no cargo: sem vigencia final");
    }

    @Test
    void onlySupervisorApprovesPepRelationship() {
        Party p = party();
        pepService.markPep(p.getId(), new PepRequest("FOREIGN", "Ministro", null, null, false, JUST));

        assertThrows(AccessDeniedException.class, () -> pepService.approvePep(p.getId(), null));

        TestUsers.login(supervisor, "SUPERVISOR");
        var approved = pepService.approvePep(p.getId(), "Aprovado pela diretoria");
        assertTrue(approved.approved());
        Party saved = partyRepository.findById(p.getId()).orElseThrow();
        assertNotNull(saved.getPepApprovalAt());
        assertEquals(userRepository.findByUsername(supervisor).orElseThrow().getId(), saved.getPepApprovalBy().getId());

        // parte que nao e PEP nao tem o que aprovar
        Party plain = party();
        assertThrows(BusinessException.class, () -> pepService.approvePep(plain.getId(), null));
    }

    @Test
    void dailyJobExpiresDuePepAndRaisesStatusChangeAlertOnce() {
        Party due = partyRepository.save(Party.builder().type("PF")
                .document(UUID.randomUUID().toString().replace("-", "").substring(0, 11))
                .name("Due").status("ACTIVE").riskLevel("HIGH").pep(true).pepStatus("ACTIVE")
                .pepEndDate(LocalDate.now().minusYears(6)).pepExpiryDate(LocalDate.now().minusDays(1)).build());
        Party notDue = partyRepository.save(Party.builder().type("PF")
                .document(UUID.randomUUID().toString().replace("-", "").substring(0, 11))
                .name("Not due").status("ACTIVE").riskLevel("HIGH").pep(true).pepStatus("ACTIVE")
                .pepEndDate(LocalDate.now().minusYears(2)).pepExpiryDate(LocalDate.now().plusYears(3)).build());

        pepService.expireDue(LocalDate.now());

        assertEquals("EXPIRED", partyRepository.findById(due.getId()).orElseThrow().getPepStatus());
        assertEquals("ACTIVE", partyRepository.findById(notDue.getId()).orElseThrow().getPepStatus());
        var alerts = alertRepository.findByPartyId(due.getId(), Pageable.unpaged()).getContent();
        assertEquals(1, alerts.size());
        assertEquals("PEP", alerts.get(0).getType());
        assertEquals("PEP_STATUS_CHANGE", alerts.get(0).getRuleCode());
        assertTrue(alertRepository.findByPartyId(notDue.getId(), Pageable.unpaged()).getContent().isEmpty());

        // segunda execucao nao duplica
        pepService.expireDue(LocalDate.now());
        assertEquals(1, alertRepository.findByPartyId(due.getId(), Pageable.unpaged()).getContent().size());
        var audit = auditService.findByEntity("PARTY", due.getId(), Pageable.unpaged()).getContent();
        assertTrue(audit.stream().anyMatch(a -> "PEP_EXPIRED".equals(a.getAction())));
    }
}
