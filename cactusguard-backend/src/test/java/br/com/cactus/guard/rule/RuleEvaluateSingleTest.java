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
class RuleEvaluateSingleTest {

    @Autowired
    private RuleEngineService ruleEngineService;

    @Autowired
    private MonitoringRuleRepository ruleRepository;

    @Test
    void evaluateSingleRuleShouldNotFail() {
        var rule = ruleRepository.findByCode("PEP_MATCH");
        assertTrue(rule.isPresent());

        assertDoesNotThrow(() -> {
            int result = ruleEngineService.evaluateRule(rule.get());
            assertTrue(result >= 0);
        });
    }
}
