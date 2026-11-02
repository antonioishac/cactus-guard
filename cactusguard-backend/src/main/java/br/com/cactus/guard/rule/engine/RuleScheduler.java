package br.com.cactus.guard.rule.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RuleScheduler {

    private final RuleEngineService ruleEngineService;
    private final RiskScoringService riskScoringService;

    @Scheduled(cron = "0 0 */6 * * *")
    public void evaluateRulesPeriodically() {
        log.info("Starting scheduled rule evaluation");
        int alerts = ruleEngineService.evaluateAllActiveRules();
        log.info("Scheduled evaluation completed. Alerts generated: {}", alerts);
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void recalculateRiskDaily() {
        log.info("Starting daily risk recalculation");
        riskScoringService.recalculateAllRiskScores();
        log.info("Daily risk recalculation completed");
    }
}
