package br.com.cactus.guard.rule;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.alert.repository.AlertTransactionRepository;
import br.com.cactus.guard.common.calendar.BusinessCalendar;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.rule.engine.RuleEngineService;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import br.com.cactus.guard.screening.entity.HighRiskJurisdiction;
import br.com.cactus.guard.screening.repository.HighRiskJurisdictionRepository;
import br.com.cactus.guard.transaction.entity.Transaction;
import br.com.cactus.guard.transaction.repository.TransactionRepository;
import br.com.cactus.guard.watchlist.entity.WatchlistEntry;
import br.com.cactus.guard.watchlist.repository.WatchlistEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Um teste por cenario do motor: parametros lidos do JSONB, evidencia, vinculos e deduplicacao. */
@SpringBootTest
@ActiveProfiles("test")
class RuleEngineScenariosTest {

    @Autowired private RuleEngineService engine;
    @Autowired private MonitoringRuleRepository ruleRepository;
    @Autowired private PartyRepository partyRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private AlertTransactionRepository alertTransactionRepository;
    @Autowired private HighRiskJurisdictionRepository jurisdictionRepository;
    @Autowired private WatchlistEntryRepository watchlistRepository;

    // ------------------------------------------------------------------ helpers

    private static Instant ms(Instant i) {
        return i.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    }

    private static String doc() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 11);
    }

    private Party party(Consumer<Party.PartyBuilder> customize) {
        Party.PartyBuilder b = Party.builder().type("PF").document(doc()).name("Scenario").status("ACTIVE").riskLevel("LOW");
        customize.accept(b);
        return partyRepository.save(b.build());
    }

    private Party party() {
        return party(b -> { });
    }

    private Transaction txn(Party p, String amount, Instant when, Consumer<Transaction.TransactionBuilder> customize) {
        Transaction.TransactionBuilder b = Transaction.builder().party(p).type("TED")
                .amount(new BigDecimal(amount)).currency("BRL").status("COMPLETED").transactionDate(when);
        customize.accept(b);
        return transactionRepository.save(b.build());
    }

    private Transaction txn(Party p, String amount) {
        return txn(p, amount, Instant.now().minus(Duration.ofHours(1)), b -> { });
    }

    /** Regra do seed com parametros sobrescritos apenas em memoria. */
    private MonitoringRule rule(String code, Map<String, Object> params) {
        MonitoringRule r = ruleRepository.findByCode(code).orElseThrow();
        r.setParameters(params);
        return r;
    }

    private List<Alert> alerts(Party p, String ruleCode) {
        return alertRepository.findByPartyId(p.getId(), Pageable.unpaged()).getContent().stream()
                .filter(a -> ruleCode.equals(a.getRuleCode())).toList();
    }

    // ------------------------------------------------------------------ TXN_HIGH_VALUE

    @Test
    void highValueRecordsRuleVersionEvidenceAmountsAndLinks() {
        Party p = party();
        Transaction old = txn(p, "70000", Instant.now().minus(Duration.ofDays(3)), b -> { });
        Transaction recent = txn(p, "80000");
        MonitoringRule rule = rule("TXN_HIGH_VALUE", Map.of("threshold_brl", 50000, "window_days", 10));

        engine.evaluateRule(rule);

        List<Alert> found = alerts(p, "TXN_HIGH_VALUE");
        assertEquals(1, found.size());
        Alert a = found.get(0);
        assertEquals(rule.getVersion(), a.getRuleVersion());
        assertEquals(rule.getId(), a.getRule().getId());
        assertEquals(0, new BigDecimal("150000").compareTo(a.getTotalAmount()));
        assertEquals(ms(old.getTransactionDate()), ms(a.getOccurredAt()));
        assertEquals(ms(BusinessCalendar.plusCalendarDays(a.getOccurredAt(), 45)), ms(a.getSelectionDeadline()));
        assertNotNull(a.getDedupKey());
        assertTrue(a.getDedupKey().startsWith("TXN_HIGH_VALUE:" + p.getId()));
        assertEquals("OPEN", a.getStatus());
        assertNotNull(a.getEvidence().get("parameters"));
        assertNotNull(a.getEvidence().get("observed"));
        assertEquals(2, alertTransactionRepository.findByAlertId(a.getId()).size());
        assertTrue(alertTransactionRepository.existsByAlertIdAndTransactionId(a.getId(), recent.getId()));
    }

    @Test
    void highValueIsDeduplicatedAcrossRuns() {
        Party p = party();
        txn(p, "90000");
        MonitoringRule rule = rule("TXN_HIGH_VALUE", Map.of("threshold_brl", 50000));
        engine.evaluateRule(rule);
        engine.evaluateRule(rule);
        assertEquals(1, alerts(p, "TXN_HIGH_VALUE").size());
    }

    @Test
    void highValueThresholdComesFromParameters() {
        Party below = party();
        Party above = party();
        txn(below, "30000");
        txn(above, "30000");
        // limiar 20000: ambos disparam; limiar 40000: nenhum
        engine.evaluateRule(rule("TXN_HIGH_VALUE", Map.of("threshold_brl", 40000)));
        assertTrue(alerts(below, "TXN_HIGH_VALUE").isEmpty());
        engine.evaluateRule(rule("TXN_HIGH_VALUE", Map.of("threshold_brl", 20000)));
        assertEquals(1, alerts(below, "TXN_HIGH_VALUE").size());
        assertEquals(1, alerts(above, "TXN_HIGH_VALUE").size());
    }

    @Test
    void highValueFallsBackToDefaultWithoutParameters() {
        Party p = party();
        txn(p, "55000");
        MonitoringRule rule = ruleRepository.findByCode("TXN_HIGH_VALUE").orElseThrow();
        rule.setParameters(null);
        rule.setThresholdValue(null);
        engine.evaluateRule(rule);
        assertEquals(1, alerts(p, "TXN_HIGH_VALUE").size());
    }

    @Test
    void severityIsEscalatedForPepAndHighRiskParties() {
        Party pep = party(b -> b.pep(true).pepStatus("ACTIVE"));
        Party highRisk = party(b -> b.riskLevel("HIGH"));
        Party normal = party();
        for (Party p : List.of(pep, highRisk, normal)) txn(p, "95000");
        MonitoringRule rule = rule("TXN_HIGH_VALUE", Map.of("threshold_brl", 90000));
        engine.evaluateRule(rule);

        String base = rule.getSeverity();
        assertEquals(base, alerts(normal, "TXN_HIGH_VALUE").get(0).getSeverity());
        String raised = br.com.cactus.guard.alert.service.AlertCreationService.escalate(base);
        assertEquals(raised, alerts(pep, "TXN_HIGH_VALUE").get(0).getSeverity());
        assertEquals(raised, alerts(highRisk, "TXN_HIGH_VALUE").get(0).getSeverity());
    }

    @Test
    void ruleThatDoesNotApplyToRiskLevelIsSkipped() {
        Party low = party();
        txn(low, "95000");
        MonitoringRule rule = rule("TXN_HIGH_VALUE", Map.of("threshold_brl", 90000));
        rule.setAppliesToRiskLevels(List.of("HIGH", "CRITICAL"));
        engine.evaluateRule(rule);
        assertTrue(alerts(low, "TXN_HIGH_VALUE").isEmpty());
    }

    // ------------------------------------------------------------------ TXN_VELOCITY

    @Test
    void velocityUsesMaxCountAndWindowParameters() {
        Party busy = party();
        Party calm = party();
        for (int i = 0; i < 4; i++) txn(busy, "100", Instant.now().minus(Duration.ofMinutes(10 + i)), b -> { });
        for (int i = 0; i < 2; i++) txn(calm, "100");
        engine.evaluateRule(rule("TXN_VELOCITY", Map.of("max_count", 4, "window_hours", 2)));
        assertEquals(1, alerts(busy, "TXN_VELOCITY").size());
        assertTrue(alerts(calm, "TXN_VELOCITY").isEmpty());
        assertEquals(4, alertTransactionRepository.findByAlertId(alerts(busy, "TXN_VELOCITY").get(0).getId()).size());
    }

    // ------------------------------------------------------------------ STRUCTURING

    @Test
    void structuringUsesThresholdBandAndMinOccurrences() {
        Party p = party();
        Party few = party();
        for (int i = 0; i < 3; i++) txn(p, "9000");
        for (int i = 0; i < 2; i++) txn(few, "9000");
        engine.evaluateRule(rule("STRUCTURING", Map.of("threshold_brl", 10000, "band_pct", 20, "min_occurrences", 3, "window_days", 7)));
        assertEquals(1, alerts(p, "STRUCTURING").size());
        assertTrue(alerts(few, "STRUCTURING").isEmpty());

        engine.evaluateRule(rule("STRUCTURING", Map.of("threshold_brl", 10000, "band_pct", 20, "min_occurrences", 2, "window_days", 7)));
        assertEquals(1, alerts(few, "STRUCTURING").size());
    }

    @Test
    void structuringIgnoresAmountsOutsideTheBand() {
        Party p = party();
        txn(p, "7000"); // abaixo da faixa 80%-100% de 10000
        txn(p, "7500");
        txn(p, "12000"); // acima do limiar
        engine.evaluateRule(rule("STRUCTURING", Map.of("threshold_brl", 10000, "band_pct", 20, "min_occurrences", 1)));
        assertTrue(alerts(p, "STRUCTURING").isEmpty());
    }

    // ------------------------------------------------------------------ UNUSUAL_PATTERN

    @Test
    void unusualPatternDetectsOutlierAgainstBaseline() {
        Party p = party();
        for (int i = 0; i < 5; i++) txn(p, "100", Instant.now().minus(Duration.ofDays(2 + i)), b -> { });
        txn(p, "10000");
        engine.evaluateRule(rule("UNUSUAL_PATTERN", Map.of("deviation_multiplier", 3.0, "min_transactions", 5)));
        List<Alert> found = alerts(p, "UNUSUAL_PATTERN");
        assertEquals(1, found.size());
        assertEquals(0, new BigDecimal("10000").compareTo(found.get(0).getTotalAmount()));
    }

    @Test
    void unusualPatternNeedsMinimumHistory() {
        Party p = party();
        for (int i = 0; i < 2; i++) txn(p, "100");
        txn(p, "10000");
        engine.evaluateRule(rule("UNUSUAL_PATTERN", Map.of("deviation_multiplier", 3.0, "min_transactions", 5)));
        assertTrue(alerts(p, "UNUSUAL_PATTERN").isEmpty());
    }

    // ------------------------------------------------------------------ PEP_TRANSACTION

    @Test
    void pepTransactionUsesLowerThresholdOnlyForPep() {
        Party pep = party(b -> b.pep(true).pepStatus("ACTIVE"));
        Party expired = party(b -> b.pep(true).pepStatus("EXPIRED"));
        Party normal = party();
        for (Party p : List.of(pep, expired, normal)) txn(p, "15000");
        engine.evaluateRule(rule("PEP_TRANSACTION", Map.of("threshold_brl", 10000, "window_days", 30)));
        assertEquals(1, alerts(pep, "PEP_TRANSACTION").size());
        assertTrue(alerts(normal, "PEP_TRANSACTION").isEmpty());
        assertTrue(alerts(expired, "PEP_TRANSACTION").isEmpty());
        assertEquals("CRITICAL", alerts(pep, "PEP_TRANSACTION").get(0).getSeverity(), "HIGH + agravante PEP");
    }

    @Test
    void pepTransactionRelatedCanBeExcluded() {
        Party related = party(b -> b.pep(true).pepStatus("RELATED"));
        txn(related, "15000");
        engine.evaluateRule(rule("PEP_TRANSACTION", Map.of("threshold_brl", 10000, "include_related", false)));
        assertTrue(alerts(related, "PEP_TRANSACTION").isEmpty());
        engine.evaluateRule(rule("PEP_TRANSACTION", Map.of("threshold_brl", 10000, "include_related", true)));
        assertEquals(1, alerts(related, "PEP_TRANSACTION").size());
    }

    // ------------------------------------------------------------------ PASS_THROUGH

    @Test
    void passThroughInflowFollowedByOutflowWithinWindow() {
        Party p = party();
        Instant t0 = Instant.now().minus(Duration.ofHours(20));
        Transaction in = txn(p, "30000", t0, b -> b.direction("IN"));
        Transaction out = txn(p, "28000", t0.plus(Duration.ofHours(5)), b -> b.direction("OUT").counterpartyName("Beneficiario X"));
        engine.evaluateRule(rule("PASS_THROUGH", Map.of("window_hours", 48, "min_out_in_ratio", 0.9,
                "min_amount_brl", 20000, "min_distinct_counterparties", 1)));
        List<Alert> found = alerts(p, "PASS_THROUGH");
        assertEquals(1, found.size());
        assertTrue(alertTransactionRepository.existsByAlertIdAndTransactionId(found.get(0).getId(), in.getId()));
        assertTrue(alertTransactionRepository.existsByAlertIdAndTransactionId(found.get(0).getId(), out.getId()));
        assertEquals(ms(in.getTransactionDate()), ms(found.get(0).getOccurredAt()));
    }

    @Test
    void passThroughNotTriggeredWhenOutflowTooSmallOrTooLate() {
        Party small = party();
        Party late = party();
        Instant t0 = Instant.now().minus(Duration.ofDays(4));
        txn(small, "30000", t0, b -> b.direction("IN"));
        txn(small, "10000", t0.plus(Duration.ofHours(2)), b -> b.direction("OUT"));
        txn(late, "30000", t0, b -> b.direction("IN"));
        txn(late, "30000", t0.plus(Duration.ofHours(72)), b -> b.direction("OUT"));
        engine.evaluateRule(rule("PASS_THROUGH", Map.of("window_hours", 48, "min_out_in_ratio", 0.9, "min_amount_brl", 20000)));
        assertTrue(alerts(small, "PASS_THROUGH").isEmpty());
        assertTrue(alerts(late, "PASS_THROUGH").isEmpty());
    }

    @Test
    void passThroughRespectsDistinctCounterparties() {
        Party p = party();
        Instant t0 = Instant.now().minus(Duration.ofHours(10));
        txn(p, "30000", t0, b -> b.direction("IN"));
        txn(p, "30000", t0.plus(Duration.ofHours(1)), b -> b.direction("OUT").counterpartyName("Unico"));
        engine.evaluateRule(rule("PASS_THROUGH", Map.of("window_hours", 48, "min_out_in_ratio", 0.9,
                "min_amount_brl", 20000, "min_distinct_counterparties", 2)));
        assertTrue(alerts(p, "PASS_THROUGH").isEmpty());
    }

    // ------------------------------------------------------------------ ROUND_AMOUNTS

    @Test
    void roundAmountsMultiplesOfOneThousand() {
        Party round = party();
        Party mixed = party();
        for (int i = 0; i < 3; i++) txn(round, "6000");
        txn(mixed, "6000");
        txn(mixed, "6001");
        txn(mixed, "6123.45");
        engine.evaluateRule(rule("ROUND_AMOUNTS", Map.of("min_amount_brl", 5000, "round_multiple", 1000,
                "min_occurrences", 3, "window_days", 30)));
        assertEquals(1, alerts(round, "ROUND_AMOUNTS").size());
        assertTrue(alerts(mixed, "ROUND_AMOUNTS").isEmpty());
    }

    @Test
    void roundAmountsJustBelowThresholdCounts() {
        Party p = party();
        for (int i = 0; i < 3; i++) txn(p, "9900.50"); // nao multiplo, mas logo abaixo de 10000
        engine.evaluateRule(rule("ROUND_AMOUNTS", Map.of("min_amount_brl", 5000, "round_multiple", 1000,
                "min_occurrences", 3, "below_threshold_brl", 10000, "below_threshold_band_pct", 2)));
        assertEquals(1, alerts(p, "ROUND_AMOUNTS").size());
    }

    // ------------------------------------------------------------------ HIGH_RISK_JURISDICTION

    @Test
    void highRiskJurisdictionMatchesCurrentlyValidEntriesOnly() {
        jurisdictionRepository.save(HighRiskJurisdiction.builder().countryCode("ZY").countryName("Teste")
                .source("INTERNAL").validFrom(LocalDate.of(2000, 1, 1)).build());
        jurisdictionRepository.save(HighRiskJurisdiction.builder().countryCode("ZX").countryName("Expirada")
                .source("INTERNAL").validFrom(LocalDate.of(2000, 1, 1)).validTo(LocalDate.of(2001, 1, 1)).build());

        Party hit = party();
        Party viaCounterparty = party();
        Party expired = party();
        Party domestic = party();
        txn(hit, "1000", Instant.now().minus(Duration.ofHours(2)), b -> b.destinationCountry("ZY"));
        txn(viaCounterparty, "1000", Instant.now().minus(Duration.ofHours(2)), b -> b.counterpartyCountry("ZY"));
        txn(expired, "1000", Instant.now().minus(Duration.ofHours(2)), b -> b.destinationCountry("ZX"));
        txn(domestic, "1000", Instant.now().minus(Duration.ofHours(2)), b -> b.destinationCountry("BR"));

        engine.evaluateRule(rule("HIGH_RISK_JURISDICTION", Map.of("sources", List.of("INTERNAL"),
                "min_amount_brl", 0, "window_days", 30)));

        assertEquals(1, alerts(hit, "HIGH_RISK_JURISDICTION").size());
        assertEquals(1, alerts(viaCounterparty, "HIGH_RISK_JURISDICTION").size());
        assertTrue(alerts(expired, "HIGH_RISK_JURISDICTION").isEmpty());
        assertTrue(alerts(domestic, "HIGH_RISK_JURISDICTION").isEmpty());
        assertNotNull(alerts(hit, "HIGH_RISK_JURISDICTION").get(0).getEvidence().get("observed"));
    }

    @Test
    void highRiskJurisdictionHonoursSourceFilterAndCheckedFields() {
        jurisdictionRepository.save(HighRiskJurisdiction.builder().countryCode("ZW").countryName("Teste")
                .source("GAFI_GREY").validFrom(LocalDate.of(2000, 1, 1)).build());
        Party p = party();
        txn(p, "1000", Instant.now().minus(Duration.ofHours(2)), b -> b.originCountry("ZW"));
        engine.evaluateRule(rule("HIGH_RISK_JURISDICTION", Map.of("sources", List.of("GAFI_BLACK"))));
        assertTrue(alerts(p, "HIGH_RISK_JURISDICTION").isEmpty(), "fonte nao selecionada");
        engine.evaluateRule(rule("HIGH_RISK_JURISDICTION", Map.of("sources", List.of("GAFI_GREY"),
                "check_fields", List.of("destination_country"))));
        assertTrue(alerts(p, "HIGH_RISK_JURISDICTION").isEmpty(), "campo origin nao verificado");
        engine.evaluateRule(rule("HIGH_RISK_JURISDICTION", Map.of("sources", List.of("GAFI_GREY"),
                "check_fields", List.of("origin_country"))));
        assertEquals(1, alerts(p, "HIGH_RISK_JURISDICTION").size());
    }

    // ------------------------------------------------------------------ PROFILE_INCOMPATIBLE

    @Test
    void profileIncompatibleAgainstIncomeRevenueAndExpectedVolume() {
        Party pf = party(b -> b.declaredMonthlyIncome(new BigDecimal("2000")));
        Party pfOk = party(b -> b.declaredMonthlyIncome(new BigDecimal("5000")));
        Party pj = party(b -> b.type("PJ").annualRevenue(new BigDecimal("120000")));
        Party expected = party(b -> b.expectedMonthlyVolume(new BigDecimal("4000")));
        Party noProfile = party();
        txn(pf, "20000");     // limite 3 x 2000 = 6000
        txn(pfOk, "12000");   // limite 3 x 5000 = 15000
        txn(pj, "20000");     // faturamento mensal 10000 x 1.5 = 15000
        txn(expected, "20000"); // esperado 4000 x 3 = 12000
        txn(noProfile, "20000");

        engine.evaluateRule(rule("PROFILE_INCOMPATIBLE", Map.of("income_multiplier", 3.0, "revenue_multiplier", 1.5,
                "window_days", 30, "min_amount_brl", 10000, "use_expected_monthly_volume", true)));

        assertEquals(1, alerts(pf, "PROFILE_INCOMPATIBLE").size());
        assertTrue(alerts(pfOk, "PROFILE_INCOMPATIBLE").isEmpty());
        assertEquals(1, alerts(pj, "PROFILE_INCOMPATIBLE").size());
        assertEquals(1, alerts(expected, "PROFILE_INCOMPATIBLE").size());
        assertTrue(alerts(noProfile, "PROFILE_INCOMPATIBLE").isEmpty(), "sem renda/faturamento/baseline nao ha como avaliar");
        var ev = alerts(pf, "PROFILE_INCOMPATIBLE").get(0).getEvidence();
        assertNotNull(ev.get("observed"));
    }

    @Test
    void profileIncompatibleIgnoresExpectedVolumeWhenDisabled() {
        Party expected = party(b -> b.expectedMonthlyVolume(new BigDecimal("4000")));
        txn(expected, "20000");
        engine.evaluateRule(rule("PROFILE_INCOMPATIBLE", Map.of("min_amount_brl", 10000, "use_expected_monthly_volume", false)));
        assertTrue(alerts(expected, "PROFILE_INCOMPATIBLE").isEmpty());
    }

    // ------------------------------------------------------------------ WATCHLIST

    @Test
    void watchlistMatchesByDocumentHashWithoutScanningAllParties() {
        String document = doc();
        Party p = partyRepository.save(Party.builder().type("PF").document(document)
                .documentHash(br.com.cactus.guard.common.util.DocumentHash.of(document))
                .name("Watchlisted").status("ACTIVE").riskLevel("LOW").build());
        watchlistRepository.save(WatchlistEntry.builder().document(document).name("Watchlisted")
                .reason("Teste motor").severity("CRITICAL").active(true).build());
        MonitoringRule rule = ruleRepository.findByCode("WATCHLIST_MATCH").orElseThrow();
        engine.evaluateRule(rule);
        engine.evaluateRule(rule);
        List<Alert> found = alerts(p, "WATCHLIST_MATCH");
        assertEquals(1, found.size());
        assertEquals("CRITICAL", found.get(0).getSeverity(), "severidade da entrada da watchlist");
    }

    @Test
    void inactiveOrUnknownRuleCodeGeneratesNothing() {
        MonitoringRule unknown = new MonitoringRule();
        unknown.setCode("NOPE");
        assertEquals(0, engine.evaluateRule(unknown));
    }
}
