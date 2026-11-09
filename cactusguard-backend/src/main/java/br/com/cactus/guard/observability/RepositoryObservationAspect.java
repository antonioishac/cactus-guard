package br.com.cactus.guard.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Span (e timer {@code db.repository}) para cada chamada a um Spring Data repository da aplicacao: cobre as consultas
 * JPA/JDBC dos modulos sem instrumentar o driver. So cria span DENTRO de uma observacao ja em andamento (requisicao
 * HTTP, consumidor Kafka, relay): fora disso nao gera traces raiz soltos. Tags de baixa cardinalidade: repositorio e metodo (nunca parametros,
 * nunca PII). Nome do span: {@code db.repository}.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class RepositoryObservationAspect {

    private static final String PKG = "br.com.cactus.guard";

    private final ObservationRegistry registry;

    @Around("target(org.springframework.data.repository.Repository) && !within(br.com.cactus.guard.observability..*)")
    public Object observe(ProceedingJoinPoint pjp) throws Throwable {
        if (registry.getCurrentObservation() == null) {
            // sem observacao em andamento (job, startup, teste): nao cria trace raiz por consulta (ruido e MDC trocado)
            return pjp.proceed();
        }
        Observation observation = Observation.createNotStarted("db.repository", registry)
                .lowCardinalityKeyValue("repository", repositoryName(pjp.getThis()))
                .lowCardinalityKeyValue("method", pjp.getSignature().getName());
        observation.start();
        try (Observation.Scope ignored = observation.openScope()) {
            return pjp.proceed();
        } catch (Throwable t) {
            observation.error(t);
            throw t;
        } finally {
            observation.stop();
        }
    }

    private static String repositoryName(Object proxy) {
        if (proxy == null) {
            return "unknown";
        }
        for (Class<?> i : proxy.getClass().getInterfaces()) {
            if (i.getName().startsWith(PKG)) {
                return i.getSimpleName();
            }
        }
        return "SpringData";
    }
}
