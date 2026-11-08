package br.com.cactus.guard.rule.engine;

import br.com.cactus.guard.tenant.service.TenantRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Agendadores do motor: cada execucao itera os tenants ativos (regras, dados e alertas sao por tenant). */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleScheduler {

    private final RuleEngineService ruleEngineService;
    private final RiskScoringService riskScoringService;
    private final TenantRunner tenantRunner;

    @Scheduled(cron = "0 0 */6 * * *")
    public void evaluateRulesPeriodically() {
        log.info("Starting scheduled rule evaluation");
        tenantRunner.forEachActiveTenant("RuleScheduler.evaluate", tenantId -> {
            int alerts = ruleEngineService.evaluateAllActiveRules();
            log.info("Scheduled evaluation completed. Alerts generated: {}", alerts);
        });
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void recalculateRiskDaily() {
        log.info("Starting daily risk recalculation");
        tenantRunner.forEachActiveTenant("RuleScheduler.risk", tenantId -> riskScoringService.recalculateAllRiskScores());
        log.info("Daily risk recalculation completed");
    }
}
