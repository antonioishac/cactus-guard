package br.com.cactus.guard.rule;

import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import br.com.cactus.guard.rule.service.MonitoringRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RuleCreateTest {

    @Autowired
    private MonitoringRuleService ruleService;

    @Autowired
    private MonitoringRuleRepository ruleRepository;

    @Test
    void createRuleShouldPersist() {
        MonitoringRule rule = new MonitoringRule();
        rule.setCode("TEST_RULE");
        rule.setName("Test Rule");
        rule.setCategory("TEST");
        rule.setSeverity("LOW");
        rule.setActive(true);

        var created = ruleService.create(rule);
        assertNotNull(created.getId());
        assertEquals("TEST_RULE", created.getCode());

        assertTrue(ruleRepository.findByCode("TEST_RULE").isPresent());
    }
}
