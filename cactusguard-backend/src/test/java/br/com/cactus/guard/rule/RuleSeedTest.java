package br.com.cactus.guard.rule;

import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RuleSeedTest {

    @Autowired
    private MonitoringRuleRepository ruleRepository;

    @Test
    void seedRulesShouldExist() {
        assertTrue(ruleRepository.findByCode("PEP_MATCH").isPresent());
        assertTrue(ruleRepository.findByCode("SANCTION_MATCH").isPresent());
        assertTrue(ruleRepository.findByCode("TXN_HIGH_VALUE").isPresent());
        assertTrue(ruleRepository.findByCode("TXN_VELOCITY").isPresent());
        assertTrue(ruleRepository.findByCode("STRUCTURING").isPresent());
        assertTrue(ruleRepository.findByCode("UNUSUAL_PATTERN").isPresent());
    }

    @Test
    void allSeedRulesShouldBeActive() {
        var rules = ruleRepository.findByActiveTrue();
        assertTrue(rules.size() >= 6);
    }
}
