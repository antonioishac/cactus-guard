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
class RuleEvaluateAllTest {

    @Autowired
    private RuleEngineService ruleEngineService;

    @Autowired
    private MonitoringRuleRepository ruleRepository;

    @Test
    void evaluateAllActiveShouldNotFail() {
        long activeBefore = ruleRepository.countByActiveTrue();
        assertTrue(activeBefore >= 6);

        assertDoesNotThrow(() -> ruleEngineService.evaluateAllActiveRules());
    }
}
