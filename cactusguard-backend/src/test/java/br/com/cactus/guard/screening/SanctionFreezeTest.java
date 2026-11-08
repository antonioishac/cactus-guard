package br.com.cactus.guard.screening;

import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.screening.entity.ScreeningResult;
import br.com.cactus.guard.screening.repository.ScreeningResultRepository;
import br.com.cactus.guard.screening.service.ScreeningService;
import br.com.cactus.guard.support.TestUsers;
import br.com.cactus.guard.transaction.dto.TransactionRequest;
import br.com.cactus.guard.transaction.service.TransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Lei 13.810/2019 + Res. BCB 44/2020: hit CONFIRMED em lista CSNU indisponibiliza ativos. */
@SpringBootTest
@ActiveProfiles("test")
class SanctionFreezeTest {

    @Autowired private ScreeningService screeningService;
    @Autowired private ScreeningResultRepository resultRepository;
    @Autowired private PartyRepository partyRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private TransactionService transactionService;
    @Autowired private AuditService auditService;

    @Autowired private br.com.cactus.guard.auth.repository.UserRepository userRepository;

    @BeforeEach
    void login() {
        TestUsers.ensure(userRepository, "admin");
        TestUsers.login("admin", "SUPERVISOR");
    }

    @AfterEach
    void logout() {
        TestUsers.logout();
    }

    private Party party() {
        return partyRepository.save(Party.builder().type("PF")
                .document(UUID.randomUUID().toString().replace("-", "").substring(0, 11))
                .name("Freeze Test").status("ACTIVE").riskLevel("LOW").build());
    }

    private ScreeningResult hit(Party p, String list) {
        return resultRepository.save(ScreeningResult.builder().party(p).listCode(list)
                .matchScore(new BigDecimal("98.0")).status("PENDING").build());
    }

    private TransactionRequest txn(Party p, String direction) {
        TransactionRequest r = new TransactionRequest();
        r.setPartyId(p.getId());
        r.setType("TED");
        r.setAmount(new BigDecimal("1000"));
        r.setDirection(direction);
        r.setTransactionDate(Instant.now());
        return r;
    }

    @Test
    void confirmedCsnuHitActivatesFreezeCriticalAlertAndCriticalRisk() {
        Party p = party();
        ScreeningResult r = hit(p, "CSNU");

        screeningService.review(r.getId(), "CONFIRMED", "Homonimia descartada: documentos coincidem");

        Party saved = partyRepository.findById(p.getId()).orElseThrow();
        assertTrue(saved.isAssetFreezeActive());
        assertNotNull(saved.getAssetFreezeAt());
        assertTrue(saved.getAssetFreezeReference().startsWith("CSNU:"));
        assertEquals("CRITICAL", saved.getRiskLevel());
        assertTrue(saved.isEddRequired());

        var alerts = alertRepository.findByPartyId(p.getId(), Pageable.unpaged()).getContent();
        assertEquals(1, alerts.size());
        assertEquals("CRITICAL", alerts.get(0).getSeverity());
        assertEquals("SCREENING_CONFIRMED", alerts.get(0).getRuleCode());

        var audit = auditService.findByEntity("PARTY", p.getId(), Pageable.unpaged()).getContent();
        assertTrue(audit.stream().anyMatch(a -> "ASSET_FREEZE_ACTIVATED".equals(a.getAction())));
        assertEquals(userRepository.findByUsername("admin").orElseThrow().getId(),
                resultRepository.findById(r.getId()).orElseThrow().getReviewedBy().getId());
    }

    @Test
    void unSanctionsListAlsoFreezes() {
        Party p = party();
        screeningService.review(hit(p, "UN_SANCTIONS").getId(), "CONFIRMED");
        assertTrue(partyRepository.findById(p.getId()).orElseThrow().isAssetFreezeActive());
    }

    @Test
    void confirmedHitOnListWithoutFreezeOnlyAlerts() {
        Party p = party();
        screeningService.review(hit(p, "PEP_BR").getId(), "CONFIRMED");
        Party saved = partyRepository.findById(p.getId()).orElseThrow();
        assertFalse(saved.isAssetFreezeActive());
        assertEquals("HIGH", saved.getRiskLevel());
        var alerts = alertRepository.findByPartyId(p.getId(), Pageable.unpaged()).getContent();
        assertEquals(1, alerts.size());
        assertEquals("HIGH", alerts.get(0).getSeverity());
    }

    @Test
    void falsePositiveDoesNotFreeze() {
        Party p = party();
        screeningService.review(hit(p, "CSNU").getId(), "FALSE_POSITIVE");
        assertFalse(partyRepository.findById(p.getId()).orElseThrow().isAssetFreezeActive());
        assertTrue(alertRepository.findByPartyId(p.getId(), Pageable.unpaged()).getContent().isEmpty());
    }

    @Test
    void reconfirmingDoesNotDuplicateAlertOrFreeze() {
        Party p = party();
        ScreeningResult r = hit(p, "CSNU");
        screeningService.review(r.getId(), "CONFIRMED");
        screeningService.review(r.getId(), "CONFIRMED");
        assertEquals(1, alertRepository.findByPartyId(p.getId(), Pageable.unpaged()).getContent().size());
    }

    @Test
    void outboundTransactionOfFrozenPartyIsRejectedWithAlertThatSurvivesRollback() {
        Party p = party();
        screeningService.review(hit(p, "CSNU").getId(), "CONFIRMED");
        int before = alertRepository.findByPartyId(p.getId(), Pageable.unpaged()).getContent().size();

        assertThrows(BusinessException.class, () -> transactionService.create(txn(p, "OUT")));
        // direcao ausente tambem e tratada como saida (conservador)
        assertThrows(BusinessException.class, () -> transactionService.create(txn(p, null)));

        var alerts = alertRepository.findByPartyId(p.getId(), Pageable.unpaged()).getContent();
        assertEquals(before + 1, alerts.size(), "um alerta por parte/dia, mesmo com varias tentativas");
        assertTrue(alerts.stream().anyMatch(a -> "ASSET_FREEZE_ATTEMPT".equals(a.getRuleCode())
                && "CRITICAL".equals(a.getSeverity())));
        var audit = auditService.findByEntity("PARTY", p.getId(), Pageable.unpaged()).getContent();
        assertEquals(2, audit.stream().filter(a -> "ASSET_FREEZE_TRANSACTION_REJECTED".equals(a.getAction())).count());
        assertTrue(transactionServiceFindsNoTransactions(p));
    }

    private boolean transactionServiceFindsNoTransactions(Party p) {
        return transactionService.findByParty(p.getId(), Pageable.unpaged()).isEmpty();
    }

    @Test
    void inboundTransactionIsAcceptedAndNonFrozenPartyIsUnaffected() {
        Party frozen = party();
        screeningService.review(hit(frozen, "CSNU").getId(), "CONFIRMED");
        assertDoesNotThrow(() -> transactionService.create(txn(frozen, "IN")));

        Party normal = party();
        assertDoesNotThrow(() -> transactionService.create(txn(normal, "OUT")));
    }
}
