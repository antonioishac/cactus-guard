package br.com.cactus.guard.db;

import br.com.cactus.guard.auth.repository.RoleRepository;
import br.com.cactus.guard.report.repository.ReportDefinitionRepository;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import br.com.cactus.guard.screening.repository.ScreeningListRepository;
import br.com.cactus.guard.support.AbstractPostgresIT;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sobe PostgreSQL real, aplica TODAS as migrations do Flyway e deixa o Hibernate validar o
 * mapeamento das entities contra o schema resultante ({@code ddl-auto=validate}).
 * <p>
 * Se alguma migration falhar ou alguma entity divergir do schema (coluna faltando, tipo
 * incompativel), o contexto Spring nao sobe e todos os testes desta classe falham.
 */
class FlywayMigrationIT extends AbstractPostgresIT {

    @Autowired
    private Flyway flyway;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private MonitoringRuleRepository ruleRepository;

    @Autowired
    private ScreeningListRepository screeningListRepository;

    @Autowired
    private ReportDefinitionRepository reportDefinitionRepository;

    @Test
    void allMigrationsShouldBeAppliedSuccessfully() throws IOException {
        MigrationInfo[] all = flyway.info().all();
        assertTrue(all.length > 0, "Nenhuma migration encontrada");
        assertEquals(0, flyway.info().pending().length, "Ha migrations pendentes");

        assertTrue(Arrays.stream(all).allMatch(m -> m.getState() == MigrationState.SUCCESS),
                () -> "Migrations fora do estado SUCCESS: " + Arrays.stream(all)
                        .filter(m -> m.getState() != MigrationState.SUCCESS)
                        .map(m -> m.getVersion() + "=" + m.getState())
                        .toList());

        Resource[] scripts = new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/V*__*.sql");
        assertEquals(scripts.length, flyway.info().applied().length,
                "Quantidade de migrations aplicadas difere dos scripts em db/migration");
    }

    @Test
    void schemaShouldMatchEntities() {
        // Chegar aqui significa que o EntityManagerFactory subiu com ddl-auto=validate.
        assertNotNull(flyway.info().current(), "Schema sem versao aplicada");
    }

    @Test
    void referenceDataSeededByMigrationsShouldExist() {
        assertTrue(roleRepository.findByName("ADMIN").isPresent());
        assertTrue(roleRepository.findByName("ANALYST").isPresent());
        assertTrue(roleRepository.findByName("SUPERVISOR").isPresent());

        assertTrue(ruleRepository.countByActiveTrue() >= 8, "Regras de monitoramento nao semeadas");
        assertTrue(ruleRepository.findByCode("WATCHLIST_MATCH").isPresent());
        assertTrue(ruleRepository.findByCode("UBO_PEP").isPresent());

        assertTrue(screeningListRepository.findByCode("PEP_BR").isPresent());
        assertTrue(screeningListRepository.findByCode("OFAC").isPresent());

        assertTrue(reportDefinitionRepository.findByCode("ALERTS_SUMMARY").isPresent());
    }
}
