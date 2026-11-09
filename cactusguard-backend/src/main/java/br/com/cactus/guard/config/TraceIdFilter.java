package br.com.cactus.guard.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identificador de correlacao da requisicao: MDC {@code traceId}, header de resposta {@code X-Trace-Id} e campo
 * {@code traceId} do corpo de erro.
 * <ul>
 *   <li><b>Com tracing (padrao)</b>: o id e o traceId do OpenTelemetry/Micrometer Tracing (32 hex) do span HTTP
 *       corrente, o mesmo que aparece nos logs, no outbox, no Kafka e no coletor OTLP. Roda logo DEPOIS do filtro de
 *       observacao do Boot (ordem HIGHEST_PRECEDENCE+1), mas antes do Spring Security: cobre 401/403 e os logs de
 *       autenticacao. O contexto de entrada e o {@code traceparent} (W3C); um {@code X-Trace-Id} do cliente nao vira o
 *       traceId (formato livre), fica no span como {@code client.trace_id} e no MDC {@code clientTraceId}.</li>
 *   <li><b>Sem tracing</b> (tracer ausente ou desligado): comportamento legado - aceita {@code X-Trace-Id} valido
 *       ou gera UUID.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";
    public static final String MDC_CLIENT_KEY = "clientTraceId";
    /** Aceita apenas ids curtos e inofensivos (evita injecao em log/header). */
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    private final Tracer tracer;

    public TraceIdFilter(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer.getIfAvailable();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String clientId = incoming != null && VALID.matcher(incoming).matches() ? incoming : null;

        Span span = tracer != null ? tracer.currentSpan() : null;
        if (span != null && span.context() != null && span.context().traceId() != null
                && !span.context().traceId().isBlank()) {
            String traceId = span.context().traceId();
            response.setHeader(HEADER, traceId);
            if (MDC.get(MDC_KEY) == null) {
                MDC.put(MDC_KEY, traceId); // o bridge ja preenche; garante quando o appender nao o fez
            }
            if (clientId != null) {
                span.tag("client.trace_id", clientId);
                MDC.put(MDC_CLIENT_KEY, clientId);
            }
            try {
                chain.doFilter(request, response);
            } finally {
                MDC.remove(MDC_CLIENT_KEY);
            }
            return;
        }

        String traceId = clientId != null ? clientId : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
