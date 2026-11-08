package br.com.cactus.guard.support;

import br.com.cactus.guard.auth.entity.Role;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.RoleRepository;
import br.com.cactus.guard.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Set;
import br.com.cactus.guard.report.entity.ReportDefinition;
import br.com.cactus.guard.report.repository.ReportDefinitionRepository;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import br.com.cactus.guard.screening.entity.ScreeningList;
import br.com.cactus.guard.screening.repository.ScreeningListRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dados de referencia do perfil "test" (H2, ddl-auto=create-drop, Flyway desligado).
 * <p>
 * Em producao estes registros vem dos INSERTs das migrations V1, V5, V6, V7, V16 e V19. Como o perfil
 * de teste nao roda Flyway, eles sao recriados aqui via JPA (e nao via data.sql) para que
 * colunas novas adicionadas as entities nao quebrem o seed. A fidelidade das migrations em si
 * e verificada por {@code FlywayMigrationIT} contra PostgreSQL real.
 */
@Component
@Profile("test")
@Order(0)
@RequiredArgsConstructor
public class TestReferenceDataSeeder implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ReportDefinitionRepository reportDefinitionRepository;
    private final MonitoringRuleRepository ruleRepository;
    private final ScreeningListRepository listRepository;
    private final br.com.cactus.guard.tenant.repository.TenantRepository tenantRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        tenant(br.com.cactus.guard.tenant.TenantContext.DEFAULT_TENANT_ID, "default", "Tenant padrao");
        tenant(br.com.cactus.guard.tenant.TenantContext.PLATFORM_TENANT_ID, "platform", "Plataforma");
        role("PLATFORM_ADMIN", "Administrador da plataforma");
        role("ADMIN", "Administrador do sistema");
        role("ANALYST", "Analista de PLD");
        role("SUPERVISOR", "Supervisor de compliance");
        role("AUDITOR", "Auditor (somente leitura)");

        // Usuário "admin" usado por vários testes (@WithMockUser("admin") precisa existir no banco).
        // Em dev ele vem de db/dev/R__dev_users.sql; aqui (H2, sem Flyway) é criado via JPA.
        adminUser();

        report("ALERTS_SUMMARY", "Resumo de Alertas", "OPERATIONAL", "Alertas por status, severidade e período");
        report("CASES_SLA", "Cases e SLA", "OPERATIONAL", "Cases abertos, vencidos e decididos");
        report("PEP_EXPOSURE", "Exposição PEP", "COMPLIANCE", "Partes PEP ativas e histórico");
        report("SCREENING_PENDING", "Screening Pendente", "COMPLIANCE", "Matches pendentes de revisão");
        report("EFFECTIVENESS", "Relatório de Efetividade", "REGULATORY", "Indicadores de efetividade PLD");

        rule("TXN_HIGH_VALUE", "Transação de alto valor", "TRANSACTIONAL", "HIGH", "Transação acima do limite configurado");
        rule("TXN_VELOCITY", "Velocidade anormal de transações", "TRANSACTIONAL", "MEDIUM", "Múltiplas transações em curto período");
        rule("PEP_MATCH", "Match com lista PEP", "SCREENING", "CRITICAL", "Parte identificada como PEP");
        rule("SANCTION_MATCH", "Match com lista de sanções", "SCREENING", "CRITICAL", "Parte em lista de sanções");
        rule("STRUCTURING", "Indício de fracionamento", "TRANSACTIONAL", "HIGH", "Padrão de fracionamento de valores");
        rule("UNUSUAL_PATTERN", "Padrão incomum de comportamento", "BEHAVIORAL", "MEDIUM", "Desvio do perfil esperado");
        rule("WATCHLIST_MATCH", "Match em Watchlist Interna", "SCREENING", "HIGH", "Gera alerta quando parte está na watchlist interna");
        rule("UBO_PEP", "UBO identificado como PEP", "SCREENING", "HIGH", "Gera alerta quando beneficiário final é marcado como PEP");

        // Cenarios V27: nascem PENDING_APPROVAL e inativos (dependem de aprovacao)
        pendingRule("CASH_50K", "Operação em espécie >= R$ 50 mil", "CASH", "HIGH");
        pendingRule("PEP_TRANSACTION", "Movimentação relevante de PEP", "PEP", "HIGH");
        pendingRule("PASS_THROUGH", "Conta de passagem", "TRANSACTIONAL", "HIGH");
        pendingRule("ROUND_AMOUNTS", "Valores redondos", "TRANSACTIONAL", "MEDIUM");
        pendingRule("HIGH_RISK_JURISDICTION", "Jurisdição de alto risco", "GEOGRAPHIC", "HIGH");
        pendingRule("PROFILE_INCOMPATIBLE", "Incompatível com perfil", "BEHAVIORAL", "HIGH");

        list("PEP_BR", "PEP Brasil", "PEP", "COAF / Receita");
        list("UN_SANCTIONS", "Sanções ONU", "SANCTION", "UN Security Council");
        freezeList("CSNU", "Sanções CSNU (Lei 13.810/2019)", "CSNU", "UN Security Council");
        list("OFAC", "OFAC SDN", "SANCTION", "US Treasury");
    }

    private void tenant(java.util.UUID id, String code, String name) {
        if (tenantRepository.existsById(id)) {
            return;
        }
        tenantRepository.save(br.com.cactus.guard.tenant.entity.Tenant.builder()
                .id(id).code(code).name(name).regulatorySegment("OTHER").build());
    }

    private void adminUser() {
        if (userRepository.findByUsername("admin").isPresent()) {
            return;
        }
        userRepository.save(User.builder()
                .username("admin")
                .email("admin@cactusguard.test")
                .password(passwordEncoder.encode("admin123"))
                .name("Administrador")
                .active(true)
                .roles(Set.of(roleRepository.findByName("ADMIN").orElseThrow()))
                .build());
    }

    private void role(String name, String description) {
        if (roleRepository.findByName(name).isPresent()) {
            return;
        }
        Role role = new Role();
        role.setName(name);
        role.setDescription(description);
        roleRepository.save(role);
    }

    private void report(String code, String name, String category, String description) {
        if (reportDefinitionRepository.findByCode(code).isPresent()) {
            return;
        }
        ReportDefinition def = new ReportDefinition();
        def.setCode(code);
        def.setName(name);
        def.setCategory(category);
        def.setDescription(description);
        def.setActive(true);
        reportDefinitionRepository.save(def);
    }

    private void rule(String code, String name, String category, String severity, String description) {
        if (ruleRepository.findByCode(code).isPresent()) {
            return;
        }
        MonitoringRule rule = new MonitoringRule();
        rule.setCode(code);
        rule.setName(name);
        rule.setCategory(category);
        rule.setSeverity(severity);
        rule.setDescription(description);
        rule.setActive(true);
        ruleRepository.save(rule);
    }

    private void pendingRule(String code, String name, String category, String severity) {
        if (ruleRepository.findByCode(code).isPresent()) {
            return;
        }
        MonitoringRule rule = new MonitoringRule();
        rule.setCode(code);
        rule.setName(name);
        rule.setCategory(category);
        rule.setSeverity(severity);
        rule.setActive(false);
        rule.setStatus("PENDING_APPROVAL");
        ruleRepository.save(rule);
    }

    private void freezeList(String code, String name, String type, String source) {
        list(code, name, type, source);
        listRepository.findByCode(code).ifPresent(l -> {
            l.setRequiresAssetFreeze(true);
            listRepository.save(l);
        });
    }

    private void list(String code, String name, String type, String source) {
        if (listRepository.findByCode(code).isPresent()) {
            return;
        }
        ScreeningList list = new ScreeningList();
        list.setCode(code);
        list.setName(name);
        list.setType(type);
        list.setSource(source);
        list.setActive(true);
        list.setRequiresAssetFreeze("UN_SANCTIONS".equals(code));
        listRepository.save(list);
    }
}
