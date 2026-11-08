package br.com.cactus.guard.support;

import br.com.cactus.guard.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Base para testes de integracao contra PostgreSQL real (Testcontainers).
 * <p>
 * Diferente do perfil "test" (H2 + create-drop + Flyway desligado), aqui o schema e criado
 * EXCLUSIVAMENTE pelas migrations do Flyway e o Hibernate roda com {@code ddl-auto=validate},
 * exatamente como em producao. Tambem e o unico lugar onde SQL nativo PostgreSQL
 * (tsvector, jsonb, triggers) e Row Level Security sao exercitados.
 * <p>
 * Papeis (multi-tenancy): o Flyway migra como o superusuario do container ("test", dono do schema), mas a
 * APLICACAO roda como {@value #APP_ROLE}: NOSUPERUSER, NOBYPASSRLS e nao-dono. Assim todo IT passa pelas policies
 * de RLS de verdade (superusuario ignora RLS). Cada teste comeca no tenant "default" (as threads dos testes
 * nao tem requisicao HTTP); testes de isolamento trocam de tenant explicitamente. Sem tenant = fail-closed.
 * <p>
 * Roda no {@code mvn verify} (failsafe, sufixo *IT). Sem Docker os testes sao pulados.
 */
@EnabledIf(value = "dockerAvailable", disabledReason = "Docker indisponivel")
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        // só migrations oficiais: o seed de dev (db/dev) não entra nos ITs
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.cache.type=simple",
        "jwt.secret=it-secret-key-for-integration-tests-only-0123456789",
        "security.crypto.keys=v1:dGVzdC1vbmx5LWVuY3J5cHRpb24ta2V5LTMyYnl0ZSE=",
        "security.crypto.active-version=v1",
        "security.document-hash.key=it-only-document-hash-pepper-0123456789abcdef",
        // eventos de dominio (Kafka/outbox) so nos ITs que estendem AbstractKafkaPostgresIT
        "cactus.events.enabled=false",
        "integrations.screening.provider=mock",
        "integrations.siscoaf.provider=mock",
        "integrations.kyc.provider=mock"
})
public abstract class AbstractPostgresIT {

    /** Papel de runtime da aplicacao nos ITs: sem superuser, sem BYPASSRLS, nao-dono das tabelas. */
    public static final String APP_ROLE = "cactus_app";
    public static final String APP_PASSWORD = "cactus_app_pw";

    /**
     * Mesma major version do docker-compose.yml. Container singleton para toda a JVM dos ITs: iniciado uma
     * unica vez (o Ryuk o remove ao fim). Com {@code @Container} estatico o container era parado/reiniciado
     * a cada classe, mas o contexto Spring em cache continuava apontando para a porta antiga, quebrando a
     * segunda IT que compartilhasse o mesmo contexto.
     */
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final boolean DOCKER_AVAILABLE = DockerClientFactory.instance().isDockerAvailable();

    static {
        if (DOCKER_AVAILABLE) {
            POSTGRES.start();
            createRuntimeRole();
        }
    }

    /**
     * Cria o papel de runtime ANTES das migrations; os privilegios padrao fazem toda tabela/sequencia criada
     * depois pelo dono (superusuario "test") ja nascer concedida ao papel.
     */
    private static void createRuntimeRole() {
        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement s = c.createStatement()) {
            s.execute("CREATE ROLE " + APP_ROLE + " LOGIN PASSWORD '" + APP_PASSWORD + "' NOSUPERUSER NOBYPASSRLS NOCREATEROLE");
            s.execute("GRANT USAGE ON SCHEMA public TO " + APP_ROLE);
            s.execute("ALTER DEFAULT PRIVILEGES FOR ROLE " + POSTGRES.getUsername()
                    + " IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO " + APP_ROLE);
            s.execute("ALTER DEFAULT PRIVILEGES FOR ROLE " + POSTGRES.getUsername()
                    + " IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO " + APP_ROLE);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao criar o papel de runtime dos ITs", e);
        }
    }

    @DynamicPropertySource
    static void datasources(DynamicPropertyRegistry registry) {
        // runtime: papel sem privilegios de bypass
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_ROLE);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        // migrations: dono do schema
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @BeforeEach
    void bindDefaultTenant() {
        TenantContext.set(TenantContext.DEFAULT_TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    /** Condição do {@code @EnabledIf}: sem Docker, os ITs são pulados (não falham). */
    static boolean dockerAvailable() {
        return DOCKER_AVAILABLE;
    }
}
