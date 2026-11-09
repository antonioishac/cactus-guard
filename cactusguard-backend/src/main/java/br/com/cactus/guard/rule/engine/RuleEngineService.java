package br.com.cactus.guard.rule.engine;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.service.AlertCreationService;
import br.com.cactus.guard.alert.service.AlertCreationService.AlertDraft;
import br.com.cactus.guard.common.util.DocumentHash;
import br.com.cactus.guard.metrics.BusinessMetrics;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import br.com.cactus.guard.screening.entity.HighRiskJurisdiction;
import br.com.cactus.guard.screening.entity.ScreeningResult;
import br.com.cactus.guard.screening.repository.HighRiskJurisdictionRepository;
import br.com.cactus.guard.screening.repository.ScreeningResultRepository;
import br.com.cactus.guard.siscoaf.service.CashCommunicationService;
import br.com.cactus.guard.transaction.entity.Transaction;
import br.com.cactus.guard.transaction.repository.TransactionRepository;
import br.com.cactus.guard.watchlist.entity.WatchlistEntry;
import br.com.cactus.guard.watchlist.repository.WatchlistEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Motor de regras de monitoramento (Carta Circular 4.001/2020).
 * <p>
 * Cada cenario le seus limiares do JSONB {@code parameters} da regra (fallback para os defaults
 * historicos), usa consultas agregadas por janela (sem varrer todas as partes) e grava no alerta:
 * regra + versao, evidencia (parametros e valores observados), valor total, data da operacao mais
 * antiga, chave de deduplicacao (regra + parte + inicio da janela) e as transacoes envolvidas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngineService {

    private static final ZoneId BRAZIL = br.com.cactus.guard.common.calendar.BusinessCalendar.BRAZIL;

    private final MonitoringRuleRepository ruleRepository;
    private final PartyRepository partyRepository;
    private final ScreeningResultRepository screeningResultRepository;
    private final WatchlistEntryRepository watchlistRepository;
    private final TransactionRepository transactionRepository;
    private final HighRiskJurisdictionRepository jurisdictionRepository;
    private final AlertCreationService alertCreation;
    private final CashCommunicationService cashCommunication;
    private final BusinessMetrics metrics;

    /** Cenarios guiados por transacao: unicos avaliados no monitoramento por evento (os demais seguem em batch). */
    static final Set<String> TRANSACTIONAL_RULES = Set.of("TXN_HIGH_VALUE", "TXN_VELOCITY", "STRUCTURING",
            "UNUSUAL_PATTERN", "PEP_TRANSACTION", "PASS_THROUGH", "ROUND_AMOUNTS", "HIGH_RISK_JURISDICTION",
            "PROFILE_INCOMPATIBLE");

    private List<MonitoringRule> activeRules() {
        Instant now = Instant.now();
        return ruleRepository.findByActiveTrue().stream()
                .filter(r -> "ACTIVE".equals(r.getStatus()))
                .filter(r -> r.getEffectiveFrom() == null || !r.getEffectiveFrom().isAfter(now))
                .filter(r -> r.getEffectiveTo() == null || r.getEffectiveTo().isAfter(now))
                .toList();
    }

    /**
     * Monitoramento quase em tempo real (consumidor de TransactionReceived): roda as regras transacionais ativas
     * apenas para a PARTE da transacao, reutilizando exatamente os mesmos cenarios do batch (mesmas consultas
     * com o filtro opcional de parte). Janelas longas continuam no batch de 6h. COE (especie) ja e avaliada
     * sincronamente na criacao da transacao e as regras de screening/watchlist nao dependem de transacao.
     */
    @Transactional
    public int evaluateForTransaction(UUID transactionId) {
        long start = System.nanoTime();
        boolean ok = false;
        try {
            int generated = doEvaluateForTransaction(transactionId);
            ok = true;
            return generated;
        } finally {
            metrics.transactionEvaluated(java.time.Duration.ofNanos(System.nanoTime() - start), ok);
        }
    }

    private int doEvaluateForTransaction(UUID transactionId) {
        Transaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
        UUID partyId = txn.getParty().getId();
        int generated = 0;
        for (MonitoringRule rule : activeRules()) {
            if (TRANSACTIONAL_RULES.contains(rule.getCode())) {
                generated += evaluateRule(rule, partyId);
            }
        }
        log.debug("Monitoramento por evento: transacao {} gerou {} alerta(s)", transactionId, generated);
        return generated;
    }

    @Transactional
    public int evaluateAllActiveRules() {
        List<MonitoringRule> rules = activeRules();
        int alertsGenerated = 0;

        for (MonitoringRule rule : rules) {
            alertsGenerated += evaluateRule(rule);
        }
        if (rules.stream().noneMatch(r -> CashCommunicationService.RULE_CODE.equals(r.getCode()))) {
            // Rede de seguranca: a COE e obrigacao legal mesmo sem a regra CASH_50K aprovada
            sweepCash(cashCommunication.currentThreshold(), 30);
        }

        log.info("Rule engine finished. Alerts generated: {}", alertsGenerated);
        return alertsGenerated;
    }

    @Transactional
    public int evaluateRule(MonitoringRule rule) {
        return evaluateRule(rule, null);
    }

    /** {@code partyScope} nulo = todas as partes (batch); preenchido = so aquela parte (evento). */
    @Transactional
    public int evaluateRule(MonitoringRule rule, UUID partyScope) {
        RuleParams p = new RuleParams(rule.getParameters());
        if (partyScope != null && !TRANSACTIONAL_RULES.contains(rule.getCode())) {
            return 0;
        }
        return switch (rule.getCode()) {
            case "PEP_MATCH" -> evaluateScreeningMatch(rule, "Match PEP identificado",
                    "identificada em lista PEP", r -> r.getListCode().contains("PEP"));
            case "SANCTION_MATCH" -> evaluateScreeningMatch(rule, "Match de Sanção identificado",
                    "identificada em lista de sanções", r -> r.getListCode().contains("SANCTION")
                            || r.getListCode().contains("OFAC") || r.getListCode().contains("UN_")
                            || r.getListCode().contains("CSNU"));
            case "TXN_HIGH_VALUE" -> evaluateHighValue(rule, p, partyScope);
            case "TXN_VELOCITY" -> evaluateVelocity(rule, p, partyScope);
            case "STRUCTURING" -> evaluateStructuring(rule, p, partyScope);
            case "UNUSUAL_PATTERN" -> evaluateUnusualPattern(rule, p, partyScope);
            case "WATCHLIST_MATCH" -> evaluateWatchlist(rule);
            case "CASH_50K" -> evaluateCash(p);
            case "PEP_TRANSACTION" -> evaluatePepTransaction(rule, p, partyScope);
            case "PASS_THROUGH" -> evaluatePassThrough(rule, p, partyScope);
            case "ROUND_AMOUNTS" -> evaluateRoundAmounts(rule, p, partyScope);
            case "HIGH_RISK_JURISDICTION" -> evaluateHighRiskJurisdiction(rule, p, partyScope);
            case "PROFILE_INCOMPATIBLE" -> evaluateProfileIncompatible(rule, p, partyScope);
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ screening / watchlist

    private int evaluateWatchlist(MonitoringRule rule) {
        Instant now = Instant.now();
        Map<String, WatchlistEntry> byHash = new HashMap<>();
        for (WatchlistEntry e : watchlistRepository.findByActiveTrue()) {
            if (e.getExpiresAt() != null && e.getExpiresAt().isBefore(now)) continue;
            byHash.put(DocumentHash.of(e.getDocument()), e);
        }
        if (byHash.isEmpty()) return 0;

        int generated = 0;
        for (Party party : partyRepository.findByDocumentHashIn(byHash.keySet())) {
            WatchlistEntry entry = byHash.get(party.getDocumentHash());
            Map<String, Object> observed = new LinkedHashMap<>();
            observed.put("watchlistEntryId", entry.getId() != null ? entry.getId().toString() : null);
            observed.put("reason", entry.getReason());
            generated += emit(rule, party, "Parte na watchlist interna",
                    "Parte " + party.getName() + " identificada na watchlist: " + entry.getReason(),
                    List.of(), null, observed, "watchlist:" + entry.getId(), null, entry.getSeverity());
        }
        log.debug("WATCHLIST_MATCH generated {} alerts", generated);
        return generated;
    }

    private int evaluateScreeningMatch(MonitoringRule rule, String title, String what,
                                       java.util.function.Predicate<ScreeningResult> filter) {
        int generated = 0;
        for (ScreeningResult r : screeningResultRepository.findByStatusFetchParty("PENDING")) {
            if (r.getListCode() == null || !filter.test(r)) continue;
            Map<String, Object> observed = new LinkedHashMap<>();
            observed.put("listCode", r.getListCode());
            observed.put("matchScore", r.getMatchScore());
            observed.put("screeningResultId", r.getId().toString());
            generated += emit(rule, r.getParty(), title,
                    "Parte " + r.getParty().getName() + " " + what + " (" + r.getListCode() + ", score: "
                            + r.getMatchScore() + ")",
                    List.of(), null, observed, "screening:" + r.getId(), r.getCreatedAt(), null);
        }
        log.debug("{} generated {} alerts", rule.getCode(), generated);
        return generated;
    }

    // ------------------------------------------------------------------ transacionais

    private int evaluateHighValue(MonitoringRule rule, RuleParams p, UUID scope) {
        BigDecimal threshold = p.decimal("threshold_brl",
                rule.getThresholdValue() != null ? rule.getThresholdValue() : new BigDecimal("50000"));
        int days = p.integer("window_days",
                rule.getThresholdPeriodDays() != null ? rule.getThresholdPeriodDays() : 30);
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        int generated = 0;
        for (var e : groupByParty(transactionRepository.findAtLeastSince(threshold, since, scope)).entrySet()) {
            List<Transaction> txns = e.getValue();
            Party party = txns.get(0).getParty();
            Map<String, Object> observed = new LinkedHashMap<>();
            observed.put("count", txns.size());
            observed.put("maxAmountBrl", txns.stream().map(RuleEngineService::brl).max(Comparator.naturalOrder()).orElse(null));
            observed.put("thresholdBrl", threshold);
            generated += emit(rule, party, "Transação de alto valor",
                    "Parte " + party.getName() + " possui " + txns.size() + " transação(ões) acima de " + threshold,
                    txns, total(txns), observed, windowKey(txns), null, null);
        }
        log.debug("TXN_HIGH_VALUE generated {} alerts", generated);
        return generated;
    }

    private int evaluateVelocity(MonitoringRule rule, RuleParams p, UUID scope) {
        int maxCount = p.integer("max_count", 10);
        int hours = p.integer("window_hours", 24);
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);

        List<Object[]> rows = transactionRepository.countByPartySinceHaving(since, maxCount, scope);
        if (rows.isEmpty()) return 0;
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] r : rows) counts.put((UUID) r[0], ((Number) r[1]).longValue());
        Map<UUID, List<Transaction>> txnsByParty = groupByParty(
                transactionRepository.findByPartiesSince(counts.keySet(), since));

        int generated = 0;
        for (var e : txnsByParty.entrySet()) {
            List<Transaction> txns = e.getValue();
            Party party = txns.get(0).getParty();
            long count = counts.get(e.getKey());
            Map<String, Object> observed = new LinkedHashMap<>();
            observed.put("count", count);
            observed.put("maxCount", maxCount);
            observed.put("windowHours", hours);
            generated += emit(rule, party, "Velocidade anormal de transações",
                    "Parte " + party.getName() + " realizou " + count + " transações nas últimas " + hours + "h",
                    txns, total(txns), observed, windowKey(txns), null, null);
        }
        log.debug("TXN_VELOCITY generated {} alerts", generated);
        return generated;
    }

    private int evaluateStructuring(MonitoringRule rule, RuleParams p, UUID scope) {
        BigDecimal threshold = p.decimal("threshold_brl", new BigDecimal("10000"));
        BigDecimal bandPct = p.decimal("band_pct", new BigDecimal("20"));
        int minOccurrences = p.integer("min_occurrences", 3);
        int days = p.integer("window_days", 7);
        BigDecimal lower = threshold.multiply(BigDecimal.ONE.subtract(bandPct.movePointLeft(2)));
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        int generated = 0;
        for (var e : groupByParty(transactionRepository.findInBandSince(lower, threshold, since, scope)).entrySet()) {
            List<Transaction> txns = e.getValue();
            if (txns.size() < minOccurrences) continue;
            Party party = txns.get(0).getParty();
            Map<String, Object> observed = new LinkedHashMap<>();
            observed.put("occurrences", txns.size());
            observed.put("thresholdBrl", threshold);
            observed.put("bandLowerBrl", lower);
            observed.put("windowDays", days);
            generated += emit(rule, party, "Possível fracionamento",
                    "Parte " + party.getName() + " possui " + txns.size()
                            + " transações próximas ao limite de " + threshold,
                    txns, total(txns), observed, windowKey(txns), null, null);
        }
        log.debug("STRUCTURING generated {} alerts", generated);
        return generated;
    }

    private int evaluateUnusualPattern(MonitoringRule rule, RuleParams p, UUID scope) {
        BigDecimal multiplier = p.decimal("deviation_multiplier", new BigDecimal("5"));
        int minTxns = p.integer("min_transactions", 5);
        int lookbackDays = p.integer("window_days", 30);
        int baselineDays = p.has("baseline_months") ? p.integer("baseline_months", 1) * 30 : lookbackDays;
        Instant now = Instant.now();

        Map<UUID, BigDecimal> avg = new HashMap<>();
        for (Object[] r : transactionRepository.averageByParty(now.minus(Math.max(baselineDays, lookbackDays), ChronoUnit.DAYS), minTxns, scope)) {
            avg.put((UUID) r[0], new BigDecimal(r[2].toString()).setScale(2, RoundingMode.HALF_UP));
        }
        if (avg.isEmpty()) return 0;
        BigDecimal globalMin = avg.values().stream().min(Comparator.naturalOrder()).get().multiply(multiplier);
        List<Transaction> candidates = transactionRepository.findAtLeastSince(globalMin, now.minus(lookbackDays, ChronoUnit.DAYS), scope);

        int generated = 0;
        for (var e : groupByParty(candidates).entrySet()) {
            BigDecimal a = avg.get(e.getKey());
            if (a == null) continue;
            BigDecimal limit = a.multiply(multiplier);
            List<Transaction> outliers = e.getValue().stream().filter(t -> brl(t).compareTo(limit) > 0).toList();
            if (outliers.isEmpty()) continue;
            Party party = outliers.get(0).getParty();
            Map<String, Object> observed = new LinkedHashMap<>();
            observed.put("averageBrl", a);
            observed.put("limitBrl", limit);
            observed.put("outliers", outliers.size());
            generated += emit(rule, party, "Padrão incomum detectado",
                    "Parte " + party.getName() + " possui transação significativamente acima da média (" + a + ")",
                    outliers, total(outliers), observed, windowKey(outliers), null, null);
        }
        log.debug("UNUSUAL_PATTERN generated {} alerts", generated);
        return generated;
    }

    private int evaluatePepTransaction(MonitoringRule rule, RuleParams p, UUID scope) {
        BigDecimal threshold = p.decimal("threshold_brl", new BigDecimal("10000"));
        int days = p.integer("window_days", 30);
        boolean includeRelated = p.bool("include_related", true);
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        int generated = 0;
        for (var e : groupByParty(transactionRepository.findPepTransactionsSince(threshold, since, scope)).entrySet()) {
            List<Transaction> txns = e.getValue();
            Party party = txns.get(0).getParty();
            if (!includeRelated && !"ACTIVE".equals(party.getPepStatus())) continue;
            Map<String, Object> observed = new LinkedHashMap<>();
            observed.put("pepStatus", party.getPepStatus());
            observed.put("count", txns.size());
            observed.put("thresholdBrl", threshold);
            generated += emit(rule, party, "Movimentação relevante de PEP",
                    "Parte PEP " + party.getName() + " com " + txns.size() + " operação(ões) acima de " + threshold,
                    txns, total(txns), observed, windowKey(txns), null, null);
        }
        return generated;
    }

    private int evaluatePassThrough(MonitoringRule rule, RuleParams p, UUID scope) {
        int windowHours = p.integer("window_hours", 48);
        BigDecimal ratio = p.decimal("min_out_in_ratio", new BigDecimal("0.90"));
        BigDecimal minAmount = p.decimal("min_amount_brl", new BigDecimal("20000"));
        int minCounterparties = p.integer("min_distinct_counterparties", 1);
        Instant since = Instant.now().minus(p.integer("lookback_days", 7), ChronoUnit.DAYS);

        List<UUID> candidates = transactionRepository.findPartyIdsWithInflowSince(minAmount, since, scope);
        if (candidates.isEmpty()) return 0;

        int generated = 0;
        for (var e : groupByParty(transactionRepository.findByPartiesSince(candidates, since)).entrySet()) {
            List<Transaction> txns = e.getValue();
            for (Transaction in : txns) {
                if (!"IN".equals(in.getDirection()) || brl(in).compareTo(minAmount) < 0) continue;
                Instant end = in.getTransactionDate().plus(windowHours, ChronoUnit.HOURS);
                List<Transaction> outs = txns.stream()
                        .filter(t -> "OUT".equals(t.getDirection()))
                        .filter(t -> !t.getTransactionDate().isBefore(in.getTransactionDate())
                                && !t.getTransactionDate().isAfter(end))
                        .toList();
                BigDecimal outTotal = total(outs);
                long counterparties = outs.stream().map(RuleEngineService::counterpartyKey).distinct().count();
                if (outs.isEmpty() || counterparties < minCounterparties
                        || outTotal.compareTo(brl(in).multiply(ratio)) < 0) {
                    continue;
                }
                Party party = in.getParty();
                List<Transaction> involved = new ArrayList<>();
                involved.add(in);
                involved.addAll(outs);
                Map<String, Object> observed = new LinkedHashMap<>();
                observed.put("inflowBrl", brl(in));
                observed.put("outflowBrl", outTotal);
                observed.put("outInRatio", outTotal.divide(brl(in), 4, RoundingMode.HALF_UP));
                observed.put("distinctCounterparties", counterparties);
                observed.put("windowHours", windowHours);
                generated += emit(rule, party, "Conta de passagem",
                        "Parte " + party.getName() + " recebeu " + brl(in) + " e enviou " + outTotal
                                + " em até " + windowHours + "h",
                        involved, outTotal.add(brl(in)), observed, windowKey(involved), null, null);
                break; // um alerta por parte e janela
            }
        }
        return generated;
    }

    private int evaluateRoundAmounts(MonitoringRule rule, RuleParams p, UUID scope) {
        BigDecimal minAmount = p.decimal("min_amount_brl", new BigDecimal("5000"));
        BigDecimal multiple = p.decimal("round_multiple", new BigDecimal("1000"));
        int minOccurrences = p.integer("min_occurrences", 3);
        int days = p.integer("window_days", 30);
        BigDecimal belowThreshold = p.has("below_threshold_brl") ? p.decimal("below_threshold_brl", null) : null;
        BigDecimal bandPct = p.decimal("below_threshold_band_pct", new BigDecimal("2"));
        BigDecimal belowLower = belowThreshold == null ? null
                : belowThreshold.multiply(BigDecimal.ONE.subtract(bandPct.movePointLeft(2)));
        BigDecimal queryMin = belowLower != null ? minAmount.min(belowLower) : minAmount;
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        int generated = 0;
        for (var e : groupByParty(transactionRepository.findAtLeastSince(queryMin, since, scope)).entrySet()) {
            List<Transaction> round = e.getValue().stream().filter(t -> {
                BigDecimal a = brl(t);
                boolean multipleHit = a.compareTo(minAmount) >= 0 && a.remainder(multiple).signum() == 0;
                boolean justBelow = belowLower != null && a.compareTo(belowLower) >= 0 && a.compareTo(belowThreshold) < 0;
                return multipleHit || justBelow;
            }).toList();
            if (round.size() < minOccurrences) continue;
            Party party = round.get(0).getParty();
            Map<String, Object> observed = new LinkedHashMap<>();
            observed.put("occurrences", round.size());
            observed.put("roundMultiple", multiple);
            observed.put("windowDays", days);
            generated += emit(rule, party, "Operações recorrentes em valores redondos",
                    "Parte " + party.getName() + " com " + round.size() + " operações em valores redondos",
                    round, total(round), observed, windowKey(round), null, null);
        }
        return generated;
    }

    private int evaluateHighRiskJurisdiction(MonitoringRule rule, RuleParams p, UUID scope) {
        List<String> sources = p.strings("sources", List.of("GAFI_BLACK", "GAFI_GREY", "INTERNAL", "TAX_HAVEN"));
        List<String> fields = p.strings("check_fields",
                List.of("origin_country", "destination_country", "counterparty_country"));
        BigDecimal minAmount = p.decimal("min_amount_brl", BigDecimal.ZERO);
        int days = p.integer("window_days", 30);
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        Map<String, List<HighRiskJurisdiction>> byCountry = jurisdictionRepository.findAll().stream()
                .filter(j -> sources.contains(j.getSource()))
                .collect(Collectors.groupingBy(j -> j.getCountryCode().toUpperCase()));
        if (byCountry.isEmpty()) return 0;

        int generated = 0;
        for (var e : groupByParty(transactionRepository.findByCountriesSince(byCountry.keySet(), minAmount, since, scope)).entrySet()) {
            List<Transaction> hits = new ArrayList<>();
            List<Map<String, Object>> matches = new ArrayList<>();
            for (Transaction t : e.getValue()) {
                LocalDate date = t.getTransactionDate().atZone(BRAZIL).toLocalDate();
                boolean hit = false;
                for (String field : fields) {
                    String country = switch (field) {
                        case "origin_country" -> t.getOriginCountry();
                        case "destination_country" -> t.getDestinationCountry();
                        case "counterparty_country" -> t.getCounterpartyCountry();
                        default -> null;
                    };
                    if (country == null) continue;
                    for (HighRiskJurisdiction j : byCountry.getOrDefault(country.toUpperCase(), List.of())) {
                        boolean valid = !j.getValidFrom().isAfter(date)
                                && (j.getValidTo() == null || !j.getValidTo().isBefore(date));
                        if (valid) {
                            hit = true;
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("field", field);
                            m.put("country", country.toUpperCase());
                            m.put("source", j.getSource());
                            m.put("transactionId", t.getId().toString());
                            matches.add(m);
                        }
                    }
                }
                if (hit) hits.add(t);
            }
            if (hits.isEmpty()) continue;
            Party party = hits.get(0).getParty();
            Map<String, Object> observed = new LinkedHashMap<>();
            observed.put("matches", matches);
            generated += emit(rule, party, "Operação com jurisdição de alto risco",
                    "Parte " + party.getName() + " com " + hits.size() + " operação(ões) envolvendo jurisdição de alto risco",
                    hits, total(hits), observed, windowKey(hits), null, null);
        }
        return generated;
    }

    private int evaluateProfileIncompatible(MonitoringRule rule, RuleParams p, UUID scope) {
        BigDecimal incomeMultiplier = p.decimal("income_multiplier", new BigDecimal("3.0"));
        BigDecimal revenueMultiplier = p.decimal("revenue_multiplier", new BigDecimal("1.5"));
        int days = p.integer("window_days", 30);
        BigDecimal minAmount = p.decimal("min_amount_brl", new BigDecimal("10000"));
        boolean useExpected = p.bool("use_expected_monthly_volume", true);
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        List<Object[]> rows = transactionRepository.volumeByParty(since, minAmount, scope);
        if (rows.isEmpty()) return 0;
        Map<UUID, BigDecimal> volume = new HashMap<>();
        for (Object[] r : rows) volume.put((UUID) r[0], new BigDecimal(r[1].toString()));

        List<Party> parties = partyRepository.findAllById(volume.keySet());
        Map<UUID, Party> hitParties = new LinkedHashMap<>();
        Map<UUID, Map<String, Object>> observedByParty = new HashMap<>();
        for (Party party : parties) {
            boolean pj = "PJ".equals(party.getType());
            BigDecimal reference = pj
                    ? (party.getAnnualRevenue() != null ? party.getAnnualRevenue().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP) : null)
                    : party.getDeclaredMonthlyIncome();
            String basis = pj ? "faturamento_mensal" : "renda_mensal";
            if (reference == null && useExpected && party.getExpectedMonthlyVolume() != null) {
                reference = party.getExpectedMonthlyVolume();
                basis = "volume_mensal_esperado";
            }
            if (reference == null || reference.signum() <= 0) continue;
            BigDecimal multiplier = pj ? revenueMultiplier : incomeMultiplier;
            BigDecimal limit = reference.multiply(multiplier)
                    .multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
            BigDecimal observedVolume = volume.get(party.getId());
            if (observedVolume.compareTo(limit) > 0) {
                hitParties.put(party.getId(), party);
                Map<String, Object> observed = new LinkedHashMap<>();
                observed.put("volumeBrl", observedVolume);
                observed.put("limitBrl", limit);
                observed.put("reference", basis);
                observed.put("referenceBrl", reference);
                observed.put("multiplier", multiplier);
                observed.put("windowDays", days);
                observedByParty.put(party.getId(), observed);
            }
        }
        if (hitParties.isEmpty()) return 0;

        int generated = 0;
        Map<UUID, List<Transaction>> txns = groupByParty(transactionRepository.findByPartiesSince(hitParties.keySet(), since));
        for (var e : hitParties.entrySet()) {
            List<Transaction> list = txns.getOrDefault(e.getKey(), List.of());
            Party party = e.getValue();
            generated += emit(rule, party, "Movimentação incompatível com o perfil",
                    "Parte " + party.getName() + " movimentou " + volume.get(e.getKey()) + " em " + days
                            + " dias, acima do compatível com o perfil declarado",
                    list, volume.get(e.getKey()), observedByParty.get(e.getKey()), windowKey(list), null, null);
        }
        return generated;
    }

    // ------------------------------------------------------------------ COE (especie)

    private int evaluateCash(RuleParams p) {
        return sweepCash(p.decimal("threshold_brl", CashCommunicationService.LEGAL_THRESHOLD),
                p.integer("window_days", 30));
    }

    private int sweepCash(BigDecimal threshold, int days) {
        int created = 0;
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        for (Transaction t : transactionRepository.findCashWithoutCoe(threshold, since)) {
            if (cashCommunication.evaluate(t, threshold).isPresent()) created++;
        }
        log.debug("CASH_50K created {} COE", created);
        return created;
    }

    // ------------------------------------------------------------------ emissao

    /** Alerta criado por um cenario. Retorna 1 se criado, 0 se ignorado (regra nao aplicavel ou duplicata). */
    private int emit(MonitoringRule rule, Party party, String title, String description,
                     List<Transaction> txns, BigDecimal totalAmount, Map<String, Object> observed,
                     String windowKey, Instant occurredAtOverride, String severityOverride) {
        if (!appliesTo(rule, party)) return 0;

        String base = severityOverride != null ? severityOverride : rule.getSeverity();
        String severity = (party.isPep() || isHighRisk(party)) ? AlertCreationService.escalate(base) : base;

        Instant occurred = occurredAtOverride;
        if (occurred == null && !txns.isEmpty()) {
            occurred = txns.stream().map(Transaction::getTransactionDate).min(Comparator.naturalOrder()).orElse(null);
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("ruleCode", rule.getCode());
        evidence.put("ruleVersion", rule.getVersion());
        evidence.put("parameters", rule.getParameters() != null ? rule.getParameters() : Map.of());
        evidence.put("observed", observed);
        evidence.put("partyPep", party.isPep());
        evidence.put("partyRiskLevel", party.getRiskLevel());
        evidence.put("severityBase", base);

        String key = rule.getCode() + ":" + party.getId() + ":" + windowKey;
        Optional<Alert> created = alertCreation.createIfAbsent(new AlertDraft(
                party, rule, rule.getCategory(), severity, title, description, rule.getCode(),
                key, totalAmount, occurred, evidence, txns));
        return created.isPresent() ? 1 : 0;
    }

    private static boolean appliesTo(MonitoringRule rule, Party party) {
        List<String> levels = rule.getAppliesToRiskLevels();
        if (levels == null || levels.isEmpty()) return true;
        String level = party.getRiskLevel() != null ? party.getRiskLevel() : "LOW";
        return levels.contains(level);
    }

    private static boolean isHighRisk(Party party) {
        return "HIGH".equals(party.getRiskLevel()) || "CRITICAL".equals(party.getRiskLevel());
    }

    // ------------------------------------------------------------------ utilitarios

    static BigDecimal brl(Transaction t) {
        return t.getAmountBrl() != null ? t.getAmountBrl() : t.getAmount();
    }

    private static BigDecimal total(List<Transaction> txns) {
        return txns.stream().map(RuleEngineService::brl).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Map<UUID, List<Transaction>> groupByParty(List<Transaction> txns) {
        Map<UUID, List<Transaction>> m = new LinkedHashMap<>();
        for (Transaction t : txns) {
            m.computeIfAbsent(t.getParty().getId(), k -> new ArrayList<>()).add(t);
        }
        return m;
    }

    /** Janela do alerta identificada pela data (Sao Paulo) da operacao mais antiga envolvida. */
    static String windowKey(List<Transaction> txns) {
        return txns.stream().map(Transaction::getTransactionDate).min(Comparator.naturalOrder())
                .map(i -> i.atZone(BRAZIL).toLocalDate().toString()).orElse("na");
    }

    private static String counterpartyKey(Transaction t) {
        if (t.getCounterpartyDocumentHash() != null) return t.getCounterpartyDocumentHash();
        if (t.getCounterpartyDocument() != null) return t.getCounterpartyDocument();
        if (t.getCounterpartyName() != null) return t.getCounterpartyName();
        return "?";
    }
}
