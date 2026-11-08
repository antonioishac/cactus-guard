package br.com.cactus.guard.support;

import br.com.cactus.guard.auth.entity.Role;
import br.com.cactus.guard.auth.repository.RoleRepository;
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
    private final ReportDefinitionRepository reportDefinitionRepository;
    private final MonitoringRuleRepository ruleRepository;
    private final ScreeningListRepository listRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        role("ADMIN", "Administrador do sistema");
        role("ANALYST", "Analista de PLD");
        role("SUPERVISOR", "Supervisor de compliance");
        role("AUDITOR", "Auditor (somente leitura)");

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

        list("PEP_BR", "PEP Brasil", "PEP", "COAF / Receita");
        list("UN_SANCTIONS", "Sanções ONU", "SANCTION", "UN Security Council");
        list("OFAC", "OFAC SDN", "SANCTION", "US Treasury");
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
        listRepository.save(list);
    }
}
