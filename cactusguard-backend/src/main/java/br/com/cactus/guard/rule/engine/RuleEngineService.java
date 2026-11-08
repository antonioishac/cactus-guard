package br.com.cactus.guard.rule.engine;

import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import br.com.cactus.guard.party.repository.PartyRepository;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.screening.entity.ScreeningResult;
import br.com.cactus.guard.screening.repository.ScreeningResultRepository;
import br.com.cactus.guard.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.watchlist.entity.WatchlistEntry;
import br.com.cactus.guard.watchlist.repository.WatchlistEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngineService {

    private final MonitoringRuleRepository ruleRepository;
    private final AlertRepository alertRepository;
    private final PartyRepository partyRepository;
    private final ScreeningResultRepository screeningResultRepository;
    private final WatchlistEntryRepository watchlistRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public int evaluateAllActiveRules() {
        List<MonitoringRule> rules = ruleRepository.findByActiveTrue();
        int alertsGenerated = 0;

        for (MonitoringRule rule : rules) {
            alertsGenerated += evaluateRule(rule);
        }

        log.info("Rule engine finished. Alerts generated: {}", alertsGenerated);
        return alertsGenerated;
    }

    @Transactional
    public int evaluateRule(MonitoringRule rule) {
        return switch (rule.getCode()) {
            case "PEP_MATCH" -> evaluatePepMatch(rule);
            case "SANCTION_MATCH" -> evaluateSanctionMatch(rule);
            case "TXN_HIGH_VALUE" -> evaluateHighValue(rule);
            case "TXN_VELOCITY" -> evaluateVelocity(rule);
            case "STRUCTURING" -> evaluateStructuring(rule);
            case "UNUSUAL_PATTERN" -> evaluateUnusualPattern(rule);
            case "WATCHLIST_MATCH" -> evaluateWatchlist(rule);
            default -> 0;
        };
    }

    private int evaluateWatchlist(MonitoringRule rule) {
        List<WatchlistEntry> activeEntries = watchlistRepository.findByActiveTrue();
        int generated = 0;

        for (WatchlistEntry entry : activeEntries) {
            var parties = partyRepository.findAll().stream()
                    .filter(p -> entry.getDocument().equals(p.getDocument()))
                    .toList();

            for (var party : parties) {
                boolean alreadyAlerted = alertRepository.findByPartyId(party.getId(), org.springframework.data.domain.Pageable.unpaged())
                        .getContent().stream()
                        .anyMatch(a -> "WATCHLIST_MATCH".equals(a.getRuleCode()) && "OPEN".equals(a.getStatus()));

                if (!alreadyAlerted) {
                    createAlert(party, rule, "Parte na watchlist interna",
                            "Parte " + party.getName() + " identificada na watchlist: " + entry.getReason());
                    generated++;
                }
            }
        }
        log.debug("WATCHLIST_MATCH generated {} alerts", generated);
        return generated;
    }

    private int evaluatePepMatch(MonitoringRule rule) {
        List<ScreeningResult> pendingPep = screeningResultRepository.findByStatus("PENDING", org.springframework.data.domain.Pageable.unpaged())
                .getContent().stream()
                .filter(r -> r.getListCode() != null && r.getListCode().contains("PEP"))
                .toList();

        int generated = 0;
        for (ScreeningResult result : pendingPep) {
            if (result.getParty() == null) continue;

            boolean alreadyAlerted = alertRepository.findByPartyId(result.getParty().getId(), org.springframework.data.domain.Pageable.unpaged())
                    .getContent().stream()
                    .anyMatch(a -> rule.getCode().equals(a.getRuleCode()) && "OPEN".equals(a.getStatus()));

            if (!alreadyAlerted) {
                createAlert(
                        result.getParty(),
                        rule,
                        "Match PEP identificado",
                        "Parte " + result.getParty().getName() + " identificada em lista PEP (score: " + result.getMatchScore() + ")"
                );
                generated++;
            }
        }
        log.debug("PEP_MATCH generated {} alerts", generated);
        return generated;
    }

    private int evaluateSanctionMatch(MonitoringRule rule) {
        List<ScreeningResult> pendingSanctions = screeningResultRepository.findByStatus("PENDING", org.springframework.data.domain.Pageable.unpaged())
                .getContent().stream()
                .filter(r -> r.getListCode() != null && (r.getListCode().contains("SANCTION") || r.getListCode().contains("OFAC") || r.getListCode().contains("UN_")))
                .toList();

        int generated = 0;
        for (ScreeningResult result : pendingSanctions) {
            if (result.getParty() == null) continue;

            boolean alreadyAlerted = alertRepository.findByPartyId(result.getParty().getId(), org.springframework.data.domain.Pageable.unpaged())
                    .getContent().stream()
                    .anyMatch(a -> rule.getCode().equals(a.getRuleCode()) && "OPEN".equals(a.getStatus()));

            if (!alreadyAlerted) {
                createAlert(
                        result.getParty(),
                        rule,
                        "Match de Sanção identificado",
                        "Parte " + result.getParty().getName() + " identificada em lista de sanções (" + result.getListCode() + ")"
                );
                generated++;
            }
        }
        log.debug("SANCTION_MATCH generated {} alerts", generated);
        return generated;
    }

    private int evaluateStructuring(MonitoringRule rule) {
        BigDecimal threshold = new BigDecimal("10000");
        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        List<br.com.cactus.guard.party.entity.Party> parties = partyRepository.findAll();
        int generated = 0;

        for (br.com.cactus.guard.party.entity.Party party : parties) {
            var txns = transactionRepository.findByPartyIdAndTransactionDateAfter(party.getId(), since);
            long nearThreshold = txns.stream()
                    .filter(t -> t.getAmount() != null && t.getAmount().compareTo(threshold.multiply(new BigDecimal("0.8"))) >= 0
                            && t.getAmount().compareTo(threshold) < 0)
                    .count();

            if (nearThreshold >= 3) {
                boolean alreadyAlerted = alertRepository.findByPartyId(party.getId(), org.springframework.data.domain.Pageable.unpaged())
                        .getContent().stream()
                        .anyMatch(a -> "STRUCTURING".equals(a.getRuleCode()) && "OPEN".equals(a.getStatus()));

                if (!alreadyAlerted) {
                    createAlert(party, rule, "Possível fracionamento",
                            "Parte " + party.getName() + " possui " + nearThreshold + " transações próximas ao limite de " + threshold);
                    generated++;
                }
            }
        }
        log.debug("STRUCTURING generated {} alerts", generated);
        return generated;
    }

    private int evaluateUnusualPattern(MonitoringRule rule) {
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        List<br.com.cactus.guard.party.entity.Party> parties = partyRepository.findAll();
        int generated = 0;

        for (br.com.cactus.guard.party.entity.Party party : parties) {
            var recentTxns = transactionRepository.findByPartyIdAndTransactionDateAfter(party.getId(), since);
            if (recentTxns.size() < 5) continue;

            BigDecimal avg = recentTxns.stream()
                    .map(t -> t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(recentTxns.size()), 2, java.math.RoundingMode.HALF_UP);

            boolean hasOutlier = recentTxns.stream()
                    .anyMatch(t -> t.getAmount() != null && t.getAmount().compareTo(avg.multiply(new BigDecimal("5"))) > 0);

            if (hasOutlier) {
                boolean alreadyAlerted = alertRepository.findByPartyId(party.getId(), org.springframework.data.domain.Pageable.unpaged())
                        .getContent().stream()
                        .anyMatch(a -> "UNUSUAL_PATTERN".equals(a.getRuleCode()) && "OPEN".equals(a.getStatus()));

                if (!alreadyAlerted) {
                    createAlert(party, rule, "Padrão incomum detectado",
                            "Parte " + party.getName() + " possui transação significativamente acima da média (" + avg + ")");
                    generated++;
                }
            }
        }
        log.debug("UNUSUAL_PATTERN generated {} alerts", generated);
        return generated;
    }

    private int evaluateHighValue(MonitoringRule rule) {
        BigDecimal threshold = rule.getThresholdValue() != null ? rule.getThresholdValue() : new BigDecimal("50000");
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);

        List<br.com.cactus.guard.party.entity.Party> parties = partyRepository.findAll();
        int generated = 0;

        for (br.com.cactus.guard.party.entity.Party party : parties) {
            var highValueTxns = transactionRepository.findHighValueByParty(party.getId(), threshold, since);
            if (!highValueTxns.isEmpty()) {
                boolean alreadyAlerted = alertRepository.findByPartyId(party.getId(), org.springframework.data.domain.Pageable.unpaged())
                        .getContent().stream()
                        .anyMatch(a -> rule.getCode().equals(a.getRuleCode()) && "OPEN".equals(a.getStatus()));

                if (!alreadyAlerted) {
                    createAlert(
                            party,
                            rule,
                            "Transação de alto valor",
                            "Parte " + party.getName() + " possui " + highValueTxns.size() + " transação(ões) acima de " + threshold
                    );
                    generated++;
                }
            }
        }
        log.debug("TXN_HIGH_VALUE generated {} alerts", generated);
        return generated;
    }

    private int evaluateVelocity(MonitoringRule rule) {
        int maxCount = 10;
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);

        List<br.com.cactus.guard.party.entity.Party> parties = partyRepository.findAll();
        int generated = 0;

        for (br.com.cactus.guard.party.entity.Party party : parties) {
            long count = transactionRepository.countByPartySince(party.getId(), since);
            if (count >= maxCount) {
                boolean alreadyAlerted = alertRepository.findByPartyId(party.getId(), org.springframework.data.domain.Pageable.unpaged())
                        .getContent().stream()
                        .anyMatch(a -> "TXN_VELOCITY".equals(a.getRuleCode()) && "OPEN".equals(a.getStatus()));

                if (!alreadyAlerted) {
                    createAlert(
                            party,
                            rule,
                            "Velocidade anormal de transações",
                            "Parte " + party.getName() + " realizou " + count + " transações nas últimas 24h"
                    );
                    generated++;
                }
            }
        }
        log.debug("TXN_VELOCITY generated {} alerts", generated);
        return generated;
    }

    public Alert createAlert(Party party, MonitoringRule rule, String title, String description) {
        Alert alert = Alert.builder()
                .party(party)
                .type(rule.getCategory())
                .severity(rule.getSeverity())
                .status("OPEN")
                .title(title)
                .description(description)
                .ruleCode(rule.getCode())
                .createdAt(Instant.now())
                .build();
        return alertRepository.save(alert);
    }
}
