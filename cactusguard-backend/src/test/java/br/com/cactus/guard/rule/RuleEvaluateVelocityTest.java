package br.com.cactus.guard.rule;

import br.com.cactus.guard.rule.engine.RuleEngineService;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RuleEvaluateVelocityTest {

    @Autowired
    private RuleEngineService ruleEngineService;

    @Autowired
    private MonitoringRuleRepository ruleRepository;

    @Test
    void evaluateVelocityRuleShouldNotFail() {
        var rule = ruleRepository.findByCode("TXN_VELOCITY");
        assertTrue(rule.isPresent());

        assertDoesNotThrow(() -> ruleEngineService.evaluateRule(rule.get()));
    }
}
