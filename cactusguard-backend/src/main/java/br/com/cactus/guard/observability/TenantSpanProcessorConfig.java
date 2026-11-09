package br.com.cactus.guard.observability;

import br.com.cactus.guard.tenant.TenantContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.SdkTracerProviderBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpanProcessor do SDK OTel que grava {@code tenant.id} em todo span no inicio (inclusive spans de bibliotecas:
 * Redis, Kafka, RestClient). Classe separada e SEM dependencias: o SDK e construido a partir deste customizer, e
 * injetar o Tracer aqui criaria ciclo de beans.
 */
@Configuration(proxyBeanMethods = false)
public class TenantSpanProcessorConfig {

    @Bean
    SdkTracerProviderBuilderCustomizer tenantSpanProcessorCustomizer() {
        return builder -> builder.addSpanProcessor(new SpanProcessor() {
            @Override
            public void onStart(Context parentContext, ReadWriteSpan span) {
                TenantContext.current().ifPresent(t -> span.setAttribute(TenantTracing.ATTRIBUTE, t.toString()));
            }

            @Override
            public boolean isStartRequired() {
                return true;
            }

            @Override
            public void onEnd(ReadableSpan span) {
                // nada
            }

            @Override
            public boolean isEndRequired() {
                return false;
            }
        });
    }
}
