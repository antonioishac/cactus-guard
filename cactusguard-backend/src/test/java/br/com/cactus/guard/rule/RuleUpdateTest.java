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
class RuleUpdateTest {

    @Autowired
    private MonitoringRuleService ruleService;

    @Autowired
    private MonitoringRuleRepository ruleRepository;

    @Test
    void updateRuleShouldPersistChanges() {
        var rules = ruleService.findAllActive();
        if (rules.isEmpty()) return;

        String code = rules.get(0).getCode();
        String originalName = rules.get(0).getName();

        MonitoringRule updated = new MonitoringRule();
        updated.setName("Updated Name");
        updated.setSeverity("CRITICAL");

        var result = ruleService.update(code, updated);
        assertEquals("Updated Name", result.getName());
        assertEquals("CRITICAL", result.getSeverity());

        updated.setName(originalName);
        ruleService.update(code, updated);
    }
}
