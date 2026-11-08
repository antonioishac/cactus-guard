package br.com.cactus.guard.case_;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.alert.service.AlertCreationService;
import br.com.cactus.guard.alert.service.AlertCreationService.AlertDraft;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.case_.dto.CaseDecisionRequest;
import br.com.cactus.guard.case_.service.CaseService;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.rule.engine.RiskScoringService;
import br.com.cactus.guard.rule.engine.RuleEngineService;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import br.com.cactus.guard.rule.repository.MonitoringRuleVersionRepository;
import br.com.cactus.guard.rule.service.MonitoringRuleService;
import br.com.cactus.guard.screening.entity.ScreeningResult;
import br.com.cactus.guard.screening.repository.ScreeningResultRepository;
import br.com.cactus.guard.screening.service.ScreeningService;
import br.com.cactus.guard.siscoaf.entity.SiscoafCommunication;
import br.com.cactus.guard.siscoaf.repository.SiscoafCommunicationRepository;
import br.com.cactus.guard.siscoaf.service.NegativeDeclarationService;
import br.com.cactus.guard.support.AbstractPostgresIT;
import br.com.cactus.guard.support.TestUsers;
import br.com.cactus.guard.transaction.dto.TransactionRequest;
import br.com.cactus.guard.transaction.entity.Transaction;
import br.com.cactus.guard.transaction.repository.TransactionRepository;
import br.com.cactus.guard.transaction.service.TransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fluxos regulatorios contra PostgreSQL real: valida HQL agregado do motor, indices unicos parciais
 * (COE por operacao, declaracao negativa por ano, dedup de alertas), CHECKs de status/quatro olhos
 * dos cases e o trigger de imutabilidade das versoes de regra.
 */
class PldRegulatoryFlowIT extends AbstractPostgresIT {

    private static final String JUST = "Analise concluida com base nos documentos e na movimentacao";

    @Autowired private RuleEngineService engine;
    @Autowired private MonitoringRuleRepository ruleRepository;
    @Autowired private MonitoringRuleVersionRepository versionRepository;
    @Autowired private MonitoringRuleService ruleService;
    @Autowired private PartyRepository partyRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionService transactionService;
    @Autowired private AlertRepository alertRepository;
    @Autowired private AlertCreationService alertCreation;
    @Autowired private CaseService caseService;
    @Autowired private SiscoafCommunicationRepository siscoafRepository;
    @Autowired private NegativeDeclarationService negativeDeclarationService;
    @Autowired private ScreeningService screeningService;
    @Autowired private ScreeningResultRepository screeningResultRepository;
    @Autowired private RiskScoringService riskScoring;
    @Autowired private UserRepository userRepository;

    @AfterEach
    void cleanup() {
        TestUsers.logout();
    }

    private Party party() {
        String doc = UUID.randomUUID().toString().replace("-", "").substring(0, 11);
        return partyRepository.save(Party.builder().type("PF").document(doc)
                .documentHash(br.com.cactus.guard.common.util.DocumentHash.of(doc))
                .name("IT Regulatorio").status("ACTIVE").riskLevel("LOW").build());
    }

    private TransactionRequest cash(Party p, String amount) {
        TransactionRequest r = new TransactionRequest();
        r.setPartyId(p.getId());
        r.setType("DEPOSIT");
        r.setAmount(new BigDecimal(amount));
        r.setDirection("IN");
        r.setPaymentMethod("CASH");
        r.setTransactionDate(Instant.now());
        return r;
    }

    @Test
    void everySeededRuleExecutesItsQueriesOnPostgres() {
        Party p = party();
        TestUsers.login("it-engine", "SUPERVISOR");
        TestUsers.ensure(userRepository, "it-engine");
        transactionService.create(cash(p, "90000"));
        for (int i = 0; i < 3; i++) {
            transactionRepository.save(Transaction.builder().party(p).type("TED").amount(new BigDecimal("9500"))
                    .currency("BRL").status("COMPLETED").direction("OUT").destinationCountry("KP")
                    .transactionDate(Instant.now().minusSeconds(600)).build());
        }
        int total = 0;
        for (var rule : ruleRepository.findAll()) {
            total += engine.evaluateRule(rule);
        }
        assertTrue(total > 0);

        // parametros JSONB semeados em V27 lidos de verdade: STRUCTURING (9500 na faixa de 10%) e jurisdicao KP (GAFI_BLACK)
        List<Alert> alerts = alertRepository.findByPartyId(p.getId(), Pageable.unpaged()).getContent();
        assertTrue(alerts.stream().anyMatch(a -> "HIGH_RISK_JURISDICTION".equals(a.getRuleCode())));
        assertTrue(alerts.stream().anyMatch(a -> "TXN_HIGH_VALUE".equals(a.getRuleCode())));
        Alert high = alerts.stream().filter(a -> "TXN_HIGH_VALUE".equals(a.getRuleCode())).findFirst().orElseThrow();
        assertNotNull(high.getEvidence());
        assertNotNull(high.getSelectionDeadline());
        assertNotNull(high.getRuleVersion());
        // repetir nao duplica (indice unico parcial + verificacao)
        long before = alertRepository.count();
        ruleRepository.findAll().forEach(engine::evaluateRule);
        assertEquals(before, alertRepository.count());
    }

    @Test
    void coeIsUniquePerTransactionAtDatabaseLevel() {
        Party p = party();
        TestUsers.login("it-coe", "SUPERVISOR");
        var resp = transactionService.create(cash(p, "60000"));
        var coe = siscoafRepository.findAll().stream()
                .filter(c -> c.getTransaction() != null && c.getTransaction().getId().equals(resp.getId())).toList();
        assertEquals(1, coe.size());
        assertEquals("APPROVED", coe.get(0).getStatus());
        assertNotNull(coe.get(0).getDeadlineAt());

        Transaction t = transactionRepository.findById(resp.getId()).orElseThrow();
        assertThrows(DataIntegrityViolationException.class, () -> siscoafRepository.saveAndFlush(
                SiscoafCommunication.builder().type("COE").status("DRAFT").transaction(t).build()));
    }

    @Test
    void negativeDeclarationIsUniquePerYear() {
        TestUsers.login("it-neg", "SUPERVISOR");
        negativeDeclarationService.generate(2002);
        assertThrows(BusinessException.class, () -> negativeDeclarationService.generate(2002));
        assertThrows(DataIntegrityViolationException.class, () -> siscoafRepository.saveAndFlush(
                SiscoafCommunication.builder().type("NEGATIVE_DECLARATION").status("DRAFT").referenceYear(2002).build()));
    }

    @Test
    void alertDedupKeyIsUniqueWhileOpen() {
        Party p = party();
        String key = "IT:" + p.getId();
        AlertDraft d = new AlertDraft(p, null, "X", "LOW", "t", "d", "IT", key, null, null, Map.of("a", 1), List.of());
        assertTrue(alertCreation.createIfAbsent(d).isPresent());
        assertTrue(alertCreation.createIfAbsent(d).isEmpty());
        assertThrows(DataIntegrityViolationException.class, () -> alertRepository.saveAndFlush(
                Alert.builder().party(p).type("X").severity("LOW").status("OPEN").title("t").dedupKey(key).build()));
    }

    @Test
    void caseFlowSatisfiesDatabaseConstraintsEndToEnd() {
        String maker = TestUsers.unique("it-maker");
        String checker = TestUsers.unique("it-checker");
        TestUsers.ensure(userRepository, maker);
        TestUsers.ensure(userRepository, checker);
        Party p = party();
        Alert a = alertCreation.create(new AlertDraft(p, null, "TRANSACTIONAL", "HIGH", "t", "d", "STRUCTURING",
                null, new BigDecimal("1000"), null, null, List.of()));

        TestUsers.login(maker, "SUPERVISOR");
        var c = caseService.createFromAlert(a.getId());
        assertNotNull(c.getAnalysisDeadline());
        CaseDecisionRequest req = new CaseDecisionRequest();
        req.setDecision("COMMUNICATE");
        req.setJustification(JUST);
        assertEquals("PENDING_APPROVAL", caseService.decide(c.getId(), req).getStatus());

        assertThrows(BusinessException.class, () -> caseService.approve(c.getId(), null));

        TestUsers.login(checker, "SUPERVISOR");
        var approved = caseService.approve(c.getId(), null);
        assertEquals("COMMUNICATE", approved.getStatus());
        assertNotNull(approved.getCommunicationDeadline());
        var cos = siscoafRepository.findByCaseEntityIdAndType(c.getId(), "COS");
        assertEquals(1, cos.size());
        assertEquals("DRAFT", cos.get(0).getStatus());
    }

    @Test
    void ruleVersioningRespectsImmutabilityTrigger() {
        String maker = TestUsers.unique("it-rmaker");
        String checker = TestUsers.unique("it-rchecker");
        TestUsers.ensure(userRepository, maker);
        TestUsers.ensure(userRepository, checker);

        // regra semeada em V27 (PENDING_APPROVAL, snapshot v1 ja existe): aprovar gera v2 e encerra v1
        TestUsers.login(checker, "SUPERVISOR");
        var approved = ruleService.approve("PASS_THROUGH", "Aprovacao do cenario no IT");
        assertEquals(2, approved.getVersion());
        var versions = versionRepository.findByRuleIdOrderByVersionDesc(approved.getId());
        assertEquals(2, versions.size());
        assertNotNull(versions.get(1).getEffectiveTo(), "v1 encerrada sem violar o trigger");
        assertNull(versions.get(0).getEffectiveTo());

        // alterar: fecha v2, regra pausa; nova aprovacao gera v3
        br.com.cactus.guard.rule.entity.MonitoringRule change = new br.com.cactus.guard.rule.entity.MonitoringRule();
        change.setSeverity("CRITICAL");
        TestUsers.login(maker, "SUPERVISOR");
        ruleService.update("PASS_THROUGH", change, "Endurecimento da severidade apos revisao do comite");
        TestUsers.login(checker, "SUPERVISOR");
        var v3 = ruleService.approve("PASS_THROUGH", "ok");
        assertEquals(3, v3.getVersion());
        assertEquals(3, versionRepository.findByRuleIdOrderByVersionDesc(v3.getId()).size());
        assertTrue(ruleService.versionEffectiveAt("PASS_THROUGH", Instant.now()).isPresent());
    }

    @Test
    void sanctionFreezeAndRiskOnPostgres() {
        TestUsers.login("it-freeze", "SUPERVISOR");
        TestUsers.ensure(userRepository, "it-freeze");
        Party p = party();
        ScreeningResult hit = screeningResultRepository.save(ScreeningResult.builder().party(p).listCode("CSNU")
                .matchScore(new BigDecimal("99")).status("PENDING").build());

        screeningService.review(hit.getId(), "CONFIRMED", "Documento e data de nascimento coincidem");

        Party saved = partyRepository.findById(p.getId()).orElseThrow();
        assertTrue(saved.isAssetFreezeActive());
        assertEquals("CRITICAL", saved.getRiskLevel());
        assertNotNull(saved.getRiskCalculatedAt());
        assertNotNull(saved.getNextReviewDate());

        TransactionRequest out = cash(p, "100");
        out.setPaymentMethod("PIX");
        out.setDirection("OUT");
        assertThrows(BusinessException.class, () -> transactionService.create(out));
        assertTrue(alertRepository.findByPartyId(p.getId(), Pageable.unpaged()).getContent().stream()
                .anyMatch(x -> "ASSET_FREEZE_ATTEMPT".equals(x.getRuleCode())));
    }

    @Test
    void recalculateAllRunsAggregatedQueriesOnPostgres() {
        Party p = party();
        riskScoring.recalculateAllRiskScores();
        Party saved = partyRepository.findById(p.getId()).orElseThrow();
        assertNotNull(saved.getRiskScore());
        assertNotNull(saved.getNextReviewDate());
        assertEquals("LOW", saved.getRiskLevel());
    }
}
