package br.com.cactus.guard.rule;

import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.rule.service.MonitoringRuleService;
import br.com.cactus.guard.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RuleCacheInvalidationTest {

    @Autowired
    private MonitoringRuleService ruleService;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanup() {
        TestUsers.logout();
    }

    @Test
    void deactivateShouldInvalidateCache() {
        String maker = TestUsers.unique("maker");
        String checker = TestUsers.unique("checker");
        TestUsers.ensure(userRepository, maker);
        TestUsers.ensure(userRepository, checker);
        String code = TestUsers.unique("C").toUpperCase();

        MonitoringRule rule = new MonitoringRule();
        rule.setCode(code);
        rule.setName("Cache test");
        rule.setCategory("TRANSACTIONAL");
        rule.setSeverity("LOW");
        TestUsers.login(maker, "SUPERVISOR");
        ruleService.create(rule);
        TestUsers.login(checker, "SUPERVISOR");
        ruleService.findAllActive(); // popula o cache
        ruleService.approve(code, "ok");
        assertTrue(ruleService.findAllActive().stream().anyMatch(r -> r.getCode().equals(code)));

        ruleService.deactivate(code, "Desativacao para validar invalidacao do cache");

        // findAllActive e @Cacheable: sem invalidacao a regra desativada continuaria na lista
        assertTrue(ruleService.findAllActive().stream().noneMatch(r -> r.getCode().equals(code)),
                "Regra desativada ainda aparece em findAllActive (cache nao invalidado)");
    }
}
