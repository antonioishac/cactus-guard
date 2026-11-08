package br.com.cactus.guard.support;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base para testes de integracao contra PostgreSQL real (Testcontainers).
 * <p>
 * Diferente do perfil "test" (H2 + create-drop + Flyway desligado), aqui o schema e criado
 * EXCLUSIVAMENTE pelas migrations do Flyway e o Hibernate roda com {@code ddl-auto=validate},
 * exatamente como em producao. Tambem e o unico lugar onde SQL nativo PostgreSQL
 * (tsvector, jsonb, triggers) e exercitado.
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
        "integrations.screening.provider=mock",
        "integrations.siscoaf.provider=mock",
        "integrations.kyc.provider=mock"
})
public abstract class AbstractPostgresIT {

    /**
     * Mesma major version do docker-compose.yml. Container singleton para toda a JVM dos ITs: iniciado uma
     * unica vez (o Ryuk o remove ao fim). Com {@code @Container} estatico o container era parado/reiniciado
     * a cada classe, mas o contexto Spring em cache continuava apontando para a porta antiga, quebrando a
     * segunda IT que compartilhasse o mesmo contexto.
     */
    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static final boolean DOCKER_AVAILABLE = DockerClientFactory.instance().isDockerAvailable();

    static {
        if (DOCKER_AVAILABLE) {
            POSTGRES.start();
        }
    }

    /** Condição do {@code @EnabledIf}: sem Docker, os ITs são pulados (não falham). */
    static boolean dockerAvailable() {
        return DOCKER_AVAILABLE;
    }
}
