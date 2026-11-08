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
        assertTrue(rules.get(0).isActive());

        try {
            ruleService.toggleActive(code, false);

            // findAllActive e @Cacheable: se o toggle nao invalidasse o cache, a regra desativada
            // continuaria aparecendo na lista de ativas
            var updated = ruleService.findAllActive();
            assertTrue(updated.stream().noneMatch(r -> r.getCode().equals(code)),
                    "Regra desativada ainda aparece em findAllActive (cache nao invalidado)");
        } finally {
            ruleService.toggleActive(code, true);
        }

        assertTrue(ruleService.findAllActive().stream().anyMatch(r -> r.getCode().equals(code)));
    }
}
