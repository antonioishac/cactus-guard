package br.com.cactus.guard.rule;

import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import br.com.cactus.guard.rule.service.MonitoringRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RuleCacheInvalidationTest {

    @Autowired
    private MonitoringRuleService ruleService;

    @Autowired
    private MonitoringRuleRepository ruleRepository;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void toggleShouldInvalidateCache() {
        var rules = ruleService.findAllActive();
        assertFalse(rules.isEmpty());

        String code = rules.get(0).getCode();
        boolean originalActive = rules.get(0).isActive();

        ruleService.toggleActive(code, !originalActive);

        var updated = ruleService.findAllActive();
        boolean newActive = updated.stream()
                .filter(r -> r.getCode().equals(code))
                .findFirst()
                .map(r -> r.isActive())
                .orElse(originalActive);

        assertNotEquals(originalActive, newActive);

        ruleService.toggleActive(code, originalActive);
    }
}
