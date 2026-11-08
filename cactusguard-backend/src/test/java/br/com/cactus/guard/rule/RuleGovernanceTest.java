package br.com.cactus.guard.rule;

import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import br.com.cactus.guard.rule.repository.MonitoringRuleVersionRepository;
import br.com.cactus.guard.rule.service.MonitoringRuleService;
import br.com.cactus.guard.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RuleGovernanceTest {

    private static final String REASON = "Calibracao do limiar apos revisao trimestral";

    @Autowired private MonitoringRuleService ruleService;
    @Autowired private MonitoringRuleRepository ruleRepository;
    @Autowired private MonitoringRuleVersionRepository versionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditService auditService;

    @AfterEach
    void cleanup() {
        TestUsers.logout();
    }

    private MonitoringRule newRule(String code) {
        MonitoringRule r = new MonitoringRule();
        r.setCode(code);
        r.setName("Regra " + code);
        r.setCategory("TRANSACTIONAL");
        r.setSeverity("MEDIUM");
        r.setParameters(Map.of("threshold_brl", 1000));
        return r;
    }

    @Test
    void createdRuleIsPendingAndInactiveUntilApprovedByAnotherUser() {
        String maker = TestUsers.unique("maker");
        String checker = TestUsers.unique("checker");
        TestUsers.ensure(userRepository, maker);
        TestUsers.ensure(userRepository, checker);
        String code = TestUsers.unique("R").toUpperCase();

        TestUsers.login(maker, "SUPERVISOR");
        var created = ruleService.create(newRule(code));
        assertEquals("PENDING_APPROVAL", created.getStatus());
        assertFalse(created.isActive(), "active deve acompanhar status");
        assertTrue(ruleService.findAllActive().stream().noneMatch(r -> r.getCode().equals(code)));

        // mesmo usuario nao aprova
        assertThrows(BusinessException.class, () -> ruleService.approve(code, "ok"));

        // sem perfil SUPERVISOR nao aprova
        TestUsers.login(checker, "ANALYST");
        assertThrows(AccessDeniedException.class, () -> ruleService.approve(code, "ok"));

        TestUsers.login(checker, "SUPERVISOR");
        var approved = ruleService.approve(code, "Aprovada pelo comite");
        assertEquals("ACTIVE", approved.getStatus());
        assertTrue(approved.isActive());
        assertEquals(1, approved.getVersion());
        assertNotNull(approved.getEffectiveFrom());
        assertTrue(ruleService.findAllActive().stream().anyMatch(r -> r.getCode().equals(code)),
                "cache de regras ativas deve ser invalidado na aprovacao");

        var versions = ruleService.versions(code);
        assertEquals(1, versions.size());
        assertNotNull(versions.get(0).approvedById());
        assertNull(versions.get(0).effectiveTo());
    }

    @Test
    void updateCreatesNewVersionClosesPreviousAndRequiresReapproval() throws Exception {
        String maker = TestUsers.unique("maker");
        String checker = TestUsers.unique("checker");
        TestUsers.ensure(userRepository, maker);
        TestUsers.ensure(userRepository, checker);
        String code = TestUsers.unique("U").toUpperCase();

        TestUsers.login(maker, "SUPERVISOR");
        ruleService.create(newRule(code));
        TestUsers.login(checker, "SUPERVISOR");
        ruleService.approve(code, "v1");
        Thread.sleep(20);
        Instant duringV1 = Instant.now();
        Thread.sleep(20);

        // justificativa obrigatoria
        MonitoringRule change = new MonitoringRule();
        change.setSeverity("HIGH");
        change.setParameters(Map.of("threshold_brl", 500));
        TestUsers.login(maker, "SUPERVISOR");
        assertThrows(BusinessException.class, () -> ruleService.update(code, change, "curta"));

        var updated = ruleService.update(code, change, REASON);
        assertEquals("PENDING_APPROVAL", updated.getStatus());
        assertFalse(updated.isActive(), "regra alterada nao opera ate ser aprovada");
        assertEquals("HIGH", updated.getSeverity());
        Thread.sleep(20);
        Instant gap = Instant.now();
        Thread.sleep(20);

        // editor nao aprova a propria alteracao
        assertThrows(BusinessException.class, () -> ruleService.approve(code, "ok"));

        TestUsers.login(checker, "SUPERVISOR");
        var v2 = ruleService.approve(code, "v2 aprovada");
        assertEquals(2, v2.getVersion());
        assertEquals("ACTIVE", v2.getStatus());
        Instant duringV2 = Instant.now();

        var versions = ruleService.versions(code);
        assertEquals(2, versions.size());
        assertNotNull(versions.get(1).effectiveTo(), "v1 deve ter vigencia encerrada");
        assertNull(versions.get(0).effectiveTo());
        assertEquals(1, ruleService.versionEffectiveAt(code, duringV1).orElseThrow().version());
        assertTrue(ruleService.versionEffectiveAt(code, gap).isEmpty(), "regra pausada nao tinha versao vigente");
        var effV2 = ruleService.versionEffectiveAt(code, duringV2).orElseThrow();
        assertEquals(2, effV2.version());
        assertEquals("HIGH", effV2.snapshot().get("severity"));
    }

    @Test
    void changesAreAudited() {
        String maker = TestUsers.unique("maker");
        TestUsers.ensure(userRepository, maker);
        String code = TestUsers.unique("A").toUpperCase();
        TestUsers.login(maker, "SUPERVISOR");
        var created = ruleService.create(newRule(code));
        var audit = auditService.findByEntity("MONITORING_RULE", created.getId(), Pageable.unpaged());
        assertTrue(audit.getContent().stream().anyMatch(a -> "RULE_CREATED".equals(a.getAction())));
    }

    @Test
    void deactivateRequiresJustificationAndReactivationRequiresApproval() {
        String maker = TestUsers.unique("maker");
        String checker = TestUsers.unique("checker");
        TestUsers.ensure(userRepository, maker);
        TestUsers.ensure(userRepository, checker);
        String code = TestUsers.unique("D").toUpperCase();

        TestUsers.login(maker, "SUPERVISOR");
        ruleService.create(newRule(code));
        TestUsers.login(checker, "SUPERVISOR");
        ruleService.approve(code, "v1");
        assertTrue(ruleService.findAllActive().stream().anyMatch(r -> r.getCode().equals(code)));

        assertThrows(BusinessException.class, () -> ruleService.deactivate(code, "x"));
        var off = ruleService.deactivate(code, "Regra gerando ruido excessivo na operacao");
        assertEquals("INACTIVE", off.getStatus());
        assertFalse(off.isActive());
        assertTrue(ruleService.findAllActive().stream().noneMatch(r -> r.getCode().equals(code)),
                "cache deve ser invalidado ao desativar");
        assertNotNull(ruleService.versions(code).get(0).effectiveTo());

        // reativar nao ativa direto: volta a PENDING_APPROVAL
        var pending = ruleService.requestActivation(code, "Reativacao apos recalibragem dos parametros");
        assertEquals("PENDING_APPROVAL", pending.getStatus());
        assertFalse(pending.isActive());
        TestUsers.login(maker, "SUPERVISOR");
        var again = ruleService.approve(code, "ok");
        assertEquals("ACTIVE", again.getStatus());
        assertTrue(again.isActive());
        assertEquals(2, again.getVersion());
    }

    @Test
    void approvingSeededPendingRuleCreatesNextVersion() {
        // regra semeada em PENDING_APPROVAL sem createdBy: qualquer SUPERVISOR aprova
        MonitoringRule seeded = ruleRepository.findByCode("ROUND_AMOUNTS").orElseThrow();
        if (!"PENDING_APPROVAL".equals(seeded.getStatus())) {
            return; // ja aprovada por outro teste da mesma JVM
        }
        String checker = TestUsers.unique("checker");
        TestUsers.ensure(userRepository, checker);
        TestUsers.login(checker, "SUPERVISOR");
        var approved = ruleService.approve("ROUND_AMOUNTS", "Aprovacao do cenario");
        assertTrue(approved.isActive());
        assertEquals(approved.getVersion(), ruleRepository.findByCode("ROUND_AMOUNTS").orElseThrow().getVersion());
        assertFalse(versionRepository.findByRuleIdOrderByVersionDesc(approved.getId()).isEmpty());
        // devolve ao estado semeado para nao interferir nos demais testes
        TestUsers.login(checker, "SUPERVISOR");
        ruleService.deactivate("ROUND_AMOUNTS", "Retorno ao estado inicial do teste de aprovacao");
    }
}
