package br.com.cactus.guard.rule;

import br.com.cactus.guard.rule.service.MonitoringRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RuleFindByCategoryTest {

    @Autowired
    private MonitoringRuleService ruleService;

    @Test
    void findByCategoryShouldReturnRules() {
        var transactional = ruleService.findByCategory("TRANSACTIONAL");
        assertFalse(transactional.isEmpty());

        var screening = ruleService.findByCategory("SCREENING");
        assertFalse(screening.isEmpty());
    }
}
