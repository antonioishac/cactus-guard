package br.com.cactus.guard.rule;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.rule.dto.MonitoringRuleResponse;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.rule.service.MonitoringRuleService;
import br.com.cactus.guard.rule.service.RuleQueryService;
import br.com.cactus.guard.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RuleQueryTest {

    @Autowired private MonitoringRuleService ruleService;
    @Autowired private RuleQueryService queryService;
    @Autowired private UserRepository userRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @AfterEach
    void logout() {
        TestUsers.logout();
    }

    private MonitoringRule newRule(String code) {
        MonitoringRule r = new MonitoringRule();
        r.setCode(code);
        r.setName("Regra " + code);
        r.setCategory("TRANSACTIONAL");
        r.setSeverity("HIGH");
        r.setThresholdValue(new BigDecimal("1000"));
        r.setParameters(Map.of("limit", 1000));
        r.setAppliesToRiskLevels(List.of("HIGH", "CRITICAL"));
        r.setCarta4001Reference("Art. 1");
        return r;
    }

    private void alert(String rule, String status) {
        alertRepository.save(Alert.builder().type("TRANSACTIONAL").severity("LOW").status(status)
                .title("t").ruleCode(rule).createdAt(Instant.now()).build());
    }

    @Test
    void pendingRulesAreListedByStatusAndExposeGovernanceFields() {
        User creator = TestUsers.ensure(userRepository, TestUsers.unique("creator"));
        TestUsers.login(creator.getUsername(), "SUPERVISOR");
        String code = TestUsers.unique("RQ").toUpperCase();
        ruleService.create(newRule(code));

        var defaultList = queryService.list(new RuleQueryService.Filter(null, null, null, code, false), PageRequest.of(0, 20));
        assertEquals(0, defaultList.getTotalElements(), "padrao lista so ativas");

        var pending = queryService.list(new RuleQueryService.Filter(List.of("PENDING_APPROVAL"), "TRANSACTIONAL", "HIGH", code, false),
                PageRequest.of(0, 20));
        assertEquals(1, pending.getTotalElements());
        MonitoringRuleResponse r = pending.getContent().get(0);
        assertEquals(creator.getId(), r.getCreatedById());
        assertNull(r.getApprovedById());
        assertEquals("Art. 1", r.getCarta4001Reference());
        assertEquals(List.of("HIGH", "CRITICAL"), r.getAppliesToRiskLevels());
        assertEquals(1000, r.getParameters().get("limit"));

        var withInactive = queryService.list(new RuleQueryService.Filter(null, null, null, code, true), PageRequest.of(0, 20));
        assertEquals(1, withInactive.getTotalElements());
    }

    @Test
    void getByCodeShowsApprovalAndActiveVersion() {
        User creator = TestUsers.ensure(userRepository, TestUsers.unique("creator"));
        User approver = TestUsers.ensure(userRepository, TestUsers.unique("approver"));
        String code = TestUsers.unique("RG").toUpperCase();
        TestUsers.login(creator.getUsername(), "SUPERVISOR");
        ruleService.create(newRule(code));
        TestUsers.login(approver.getUsername(), "SUPERVISOR");
        ruleService.approve(code, "ok");

        MonitoringRuleResponse r = queryService.get(code);
        assertEquals("ACTIVE", r.getStatus());
        assertEquals(approver.getId(), r.getApprovedById());
        assertEquals(creator.getId(), r.getCreatedById());
        assertEquals(r.getVersion(), r.getActiveVersion());
        assertThrows(IllegalArgumentException.class, () -> queryService.get("NAO_EXISTE"));
    }

    @Test
    void metricsCountGeneratedAndFalsePositiveRateOverClosedAlerts() {
        User u = TestUsers.ensure(userRepository, TestUsers.unique("sup"));
        TestUsers.login(u.getUsername(), "SUPERVISOR");
        String code = TestUsers.unique("RM").toUpperCase();
        ruleService.create(newRule(code));

        var empty = queryService.metrics(code, null, null);
        assertEquals(0, empty.alertsGenerated());
        assertNull(empty.falsePositiveRate());

        alert(code, "OPEN");
        alert(code, "FALSE_POSITIVE");
        alert(code, "FALSE_POSITIVE");
        alert(code, "FALSE_POSITIVE");
        alert(code, "CLOSED");
        // determinismo: recua created_at (gerado no PrePersist) para longe do limite superior "agora" da janela
        jdbc.update("update alerts set created_at = ? where rule_code = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(3600)), code);

        var m = queryService.metrics(code, null, null);
        assertEquals(5, m.alertsGenerated());
        assertEquals(4, m.alertsClosed());
        assertEquals(3, m.falsePositives());
        assertEquals(0.75, m.falsePositiveRate(), 1e-9);

        var listed = queryService.list(new RuleQueryService.Filter(List.of("PENDING_APPROVAL"), null, null, code, false), PageRequest.of(0, 5))
                .getContent().get(0);
        assertEquals(5L, listed.getAlertsGenerated());
        assertEquals(0.75, listed.getFalsePositiveRate(), 1e-9);

        // fora do periodo
        var past = queryService.metrics(code, Instant.now().minusSeconds(86400L * 400), Instant.now().minusSeconds(86400L * 300));
        assertEquals(0, past.alertsGenerated());
    }
}
