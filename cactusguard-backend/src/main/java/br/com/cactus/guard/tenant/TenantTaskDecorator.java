package br.com.cactus.guard.tenant;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Propaga tenant e MDC (traceId) da thread que submete a tarefa {@code @Async} para a thread do executor,
 * e SEMPRE limpa ao final (threads de pool/virtuais sao reutilizadas).
 */
@Component
public class TenantTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        UUID tenant = TenantContext.current().orElse(null);
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (mdc != null) {
                    MDC.setContextMap(mdc);
                }
                TenantContext.set(tenant);
                task.run();
            } finally {
                TenantContext.clear();
                MDC.clear();
            }
        };
    }
}
