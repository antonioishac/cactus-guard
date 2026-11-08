package br.com.cactus.guard.siscoaf;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.case_.dto.CaseDecisionRequest;
import br.com.cactus.guard.case_.service.CaseService;
import br.com.cactus.guard.common.calendar.BusinessCalendar;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.rule.engine.RuleEngineService;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import br.com.cactus.guard.siscoaf.entity.SiscoafCommunication;
import br.com.cactus.guard.siscoaf.repository.SiscoafCommunicationRepository;
import br.com.cactus.guard.siscoaf.service.NegativeDeclarationService;
import br.com.cactus.guard.siscoaf.service.SiscoafService;
import br.com.cactus.guard.support.TestUsers;
import br.com.cactus.guard.transaction.dto.TransactionRequest;
import br.com.cactus.guard.transaction.entity.Transaction;
import br.com.cactus.guard.transaction.repository.TransactionRepository;
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
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SiscoafPldTest {

    @Autowired private TransactionService transactionService;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private SiscoafCommunicationRepository siscoafRepository;
    @Autowired private SiscoafService siscoafService;
    @Autowired private NegativeDeclarationService negativeDeclarationService;
    @Autowired private PartyRepository partyRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private CaseService caseService;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditService auditService;
    @Autowired private RuleEngineService ruleEngine;
    @Autowired private MonitoringRuleRepository ruleRepository;

    @BeforeEach
    void login() {
        TestUsers.login("admin", "SUPERVISOR");
    }

    @AfterEach
    void logout() {
        TestUsers.logout();
    }

    private Party party() {
        return partyRepository.save(Party.builder()
                .type("PF")
                .document(UUID.randomUUID().toString().replace("-", "").substring(0, 11))
                .name("Siscoaf PLD").status("ACTIVE").riskLevel("LOW").build());
    }

    private TransactionRequest cashRequest(Party p, String amount) {
        TransactionRequest r = new TransactionRequest();
        r.setPartyId(p.getId());
        r.setType("DEPOSIT");
        r.setAmount(new BigDecimal(amount));
        r.setDirection("IN");
        r.setPaymentMethod("CASH");
        r.setTransactionDate(Instant.now());
        return r;
    }

    // ------------------------------------------------------------------ COE

    @Test
    void cashAtOrAboveThresholdCreatesCoeOnIngestion() {
        Party p = party();
        var resp = transactionService.create(cashRequest(p, "50000.00"));
        assertTrue(resp.isCash());

        var coe = siscoafRepository.findAll().stream()
                .filter(c -> c.getTransaction() != null && c.getTransaction().getId().equals(resp.getId()))
                .toList();
        assertEquals(1, coe.size());
        SiscoafCommunication c = coe.get(0);
        assertEquals("COE", c.getType());
        assertEquals("APPROVED", c.getStatus(), "pronta para envio, sem envio automatico");
        assertNull(c.getSubmittedAt());
        assertEquals(0, new BigDecimal("50000.00").compareTo(c.getAmount()));
        LocalDate expected = BusinessCalendar.nationalOnly().nextBusinessDay(LocalDate.now(BusinessCalendar.BRAZIL));
        assertEquals(expected, c.getDeadlineAt().atZone(BusinessCalendar.BRAZIL).toLocalDate());

        var audit = auditService.findByEntity("SISCOAF_COMMUNICATION", c.getId(), Pageable.unpaged());
        assertTrue(audit.getContent().stream().anyMatch(a -> "COE_AUTO_CREATED".equals(a.getAction())));
    }

    @Test
    void cashBelowThresholdOrNonCashDoesNotCreateCoe() {
        Party p = party();
        var below = transactionService.create(cashRequest(p, "49999.99"));
        TransactionRequest nonCash = cashRequest(p, "90000.00");
        nonCash.setPaymentMethod("TED");
        var ted = transactionService.create(nonCash);

        assertTrue(siscoafRepository.findAll().stream().noneMatch(c -> c.getTransaction() != null
                && (c.getTransaction().getId().equals(below.getId()) || c.getTransaction().getId().equals(ted.getId()))));
    }

    @Test
    void cashWithdrawalProvisionCountsAsCash() {
        Party p = party();
        TransactionRequest r = cashRequest(p, "75000");
        r.setPaymentMethod("TED");
        r.setDirection("OUT");
        r.setCashWithdrawalProvision(true);
        var resp = transactionService.create(r);
        assertTrue(siscoafRepository.findAll().stream().anyMatch(c -> c.getTransaction() != null
                && c.getTransaction().getId().equals(resp.getId()) && "COE".equals(c.getType())));
    }

    @Test
    void foreignCurrencyCashNeedsBrlAmount() {
        Party p = party();
        TransactionRequest usd = cashRequest(p, "60000");
        usd.setCurrency("USD");
        var noBrl = transactionService.create(usd);
        assertTrue(siscoafRepository.findAll().stream()
                .noneMatch(c -> c.getTransaction() != null && c.getTransaction().getId().equals(noBrl.getId())));

        TransactionRequest usd2 = cashRequest(p, "10000");
        usd2.setCurrency("USD");
        usd2.setAmountBrl(new BigDecimal("55000"));
        var withBrl = transactionService.create(usd2);
        assertTrue(siscoafRepository.findAll().stream()
                .anyMatch(c -> c.getTransaction() != null && c.getTransaction().getId().equals(withBrl.getId())));
    }

    @Test
    void batchSweepCreatesMissingCoeAndIsIdempotent() {
        Party p = party();
        // transacao gravada fora do fluxo de ingestao (ex.: migracao)
        Transaction t = transactionRepository.save(Transaction.builder()
                .party(p).type("DEPOSIT").amount(new BigDecimal("80000")).currency("BRL").status("COMPLETED")
                .cash(true).direction("IN").transactionDate(Instant.now()).build());

        var rule = ruleRepository.findByCode("CASH_50K").orElseThrow();
        assertTrue(ruleEngine.evaluateRule(rule) >= 1);
        long first = siscoafRepository.findAll().stream()
                .filter(c -> c.getTransaction() != null && c.getTransaction().getId().equals(t.getId())).count();
        assertEquals(1, first);

        ruleEngine.evaluateRule(rule);
        long second = siscoafRepository.findAll().stream()
                .filter(c -> c.getTransaction() != null && c.getTransaction().getId().equals(t.getId())).count();
        assertEquals(1, second, "indice unico/verificacao impede duplicata");
    }

    @Test
    void thresholdIsReadFromRuleParameters() {
        Party p = party();
        Transaction t = transactionRepository.save(Transaction.builder()
                .party(p).type("DEPOSIT").amount(new BigDecimal("20000")).currency("BRL").status("COMPLETED")
                .cash(true).direction("IN").transactionDate(Instant.now()).build());
        var rule = ruleRepository.findByCode("CASH_50K").orElseThrow();
        rule.setParameters(Map.of("threshold_brl", 10000));
        ruleEngine.evaluateRule(rule);
        assertTrue(siscoafRepository.findAll().stream()
                .anyMatch(c -> c.getTransaction() != null && c.getTransaction().getId().equals(t.getId())));
    }

    // ------------------------------------------------------------------ declaracao negativa

    @Test
    void negativeDeclarationOnlyWithoutSentCommunications() {
        // ano sem envios -> gera, uma por ano
        SiscoafCommunication d = negativeDeclarationService.generate(2001);
        assertEquals("NEGATIVE_DECLARATION", d.getType());
        assertEquals(2001, d.getReferenceYear());
        assertThrows(BusinessException.class, () -> negativeDeclarationService.generate(2001));

        // ano com COS enviada -> 422
        siscoafRepository.save(SiscoafCommunication.builder()
                .type("COS").status("SUBMITTED")
                .submittedAt(ZonedDateTime.of(2003, 6, 10, 12, 0, 0, 0, BusinessCalendar.BRAZIL).toInstant())
                .build());
        assertThrows(BusinessException.class, () -> negativeDeclarationService.generate(2003));

        // COE so rascunho/nao enviada nao bloqueia
        siscoafRepository.save(SiscoafCommunication.builder().type("COE").status("DRAFT")
                .createdAt(Instant.now()).build());
        assertDoesNotThrow(() -> negativeDeclarationService.generate(2004));

        // ano futuro invalido
        assertThrows(BusinessException.class,
                () -> negativeDeclarationService.generate(LocalDate.now().getYear() + 1));
    }

    // ------------------------------------------------------------------ COS depende de decisao humana

    @Test
    void cosCannotBeSubmittedBeforeApprovedCommunicateDecision() {
        Party p = party();
        Alert a = alertRepository.save(Alert.builder().party(p).type("TRANSACTIONAL").severity("HIGH")
                .status("OPEN").title("t").ruleCode("STRUCTURING").createdAt(Instant.now()).build());
        var c = caseService.createFromAlert(a.getId());
        var draft = siscoafService.createDraft(c.getId(), "COS", Map.of());
        assertThrows(BusinessException.class, () -> siscoafService.submit(draft.getId()));

        // fluxo humano completo: decide (analista) + aprova (outro supervisor)
        String maker = TestUsers.unique("maker");
        String checker = TestUsers.unique("checker");
        TestUsers.ensure(userRepository, maker);
        TestUsers.ensure(userRepository, checker);
        TestUsers.login(maker, "SUPERVISOR");
        CaseDecisionRequest req = new CaseDecisionRequest();
        req.setDecision("COMMUNICATE");
        req.setJustification("Indicios consistentes de fracionamento para evasao de controles");
        caseService.decide(c.getId(), req);
        TestUsers.login(checker, "SUPERVISOR");
        caseService.approve(c.getId(), null);

        var cosDrafts = siscoafRepository.findByCaseEntityIdAndType(c.getId(), "COS");
        assertEquals(1, cosDrafts.size(), "rascunho manual reaproveitado, sem duplicar COS");
        var generated = cosDrafts.get(0);
        assertNotNull(generated.getDecidedAt());
        assertNotNull(generated.getDeadlineAt());
        var sent = siscoafService.submit(generated.getId());
        assertEquals("SUBMITTED", sent.getStatus());
        assertNotNull(sent.getSubmittedAt());
        assertEquals("COMMUNICATED", caseService.findById(c.getId()).getStatus());
    }

    @Test
    void cancelAndRectifyRequireJustificationAndRightStatus() {
        SiscoafCommunication draft = siscoafRepository.save(SiscoafCommunication.builder()
                .type("COE").status("DRAFT").payload(Map.of("k", "v")).build());
        assertThrows(BusinessException.class, () -> siscoafService.cancel(draft.getId(), "curta"));
        var cancelled = siscoafService.cancel(draft.getId(), "Operacao duplicada por erro de ingestao");
        assertEquals("CANCELLED", cancelled.getStatus());
        assertNotNull(cancelled.getCancelledAt());

        // retificar so comunicacao enviada
        assertThrows(BusinessException.class,
                () -> siscoafService.rectify(draft.getId(), "Correcao de valor informado no campo", null));
        SiscoafCommunication sent = siscoafRepository.save(SiscoafCommunication.builder()
                .type("COE").status("SUBMITTED").submittedAt(Instant.now()).payload(Map.of("k", "v")).build());
        var rect = siscoafService.rectify(sent.getId(), "Correcao de valor informado no campo", Map.of("k", "v2"));
        assertEquals("DRAFT", rect.getStatus());
        assertEquals(sent.getId(), rect.getOriginalCommunicationId());
        assertEquals("RECTIFIED", siscoafRepository.findById(sent.getId()).orElseThrow().getStatus());
    }
}
