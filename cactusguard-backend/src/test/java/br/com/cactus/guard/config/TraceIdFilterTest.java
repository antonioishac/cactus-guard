package br.com.cactus.guard.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.TraceContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceIdFilterTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<Tracer> provider(Tracer t) {
        ObjectProvider<Tracer> p = Mockito.mock(ObjectProvider.class);
        Mockito.when(p.getIfAvailable()).thenReturn(t);
        return p;
    }

    @Test
    void usesTheOpenTelemetryTraceIdOfTheCurrentSpan() throws Exception {
        String otel = "4bf92f3577b34da6a3ce929d0e0e4736";
        Tracer tracer = Mockito.mock(Tracer.class);
        Span span = Mockito.mock(Span.class);
        TraceContext ctx = Mockito.mock(TraceContext.class);
        Mockito.when(tracer.currentSpan()).thenReturn(span);
        Mockito.when(span.context()).thenReturn(ctx);
        Mockito.when(ctx.traceId()).thenReturn(otel);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/x");
        req.addHeader("X-Trace-Id", "client-supplied-123456");
        MockHttpServletResponse res = new MockHttpServletResponse();
        new TraceIdFilter(provider(tracer)).doFilter(req, res, new MockFilterChain());

        assertEquals(otel, res.getHeader("X-Trace-Id"));
        Mockito.verify(span).tag("client.trace_id", "client-supplied-123456");
    }

    @Test
    void withoutTracerKeepsLegacyBehaviour() throws Exception {
        TraceIdFilter f = new TraceIdFilter(provider(null));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/x");
        req.addHeader("X-Trace-Id", "trace-abc-12345678");
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        assertEquals("trace-abc-12345678", res.getHeader("X-Trace-Id"));
        assertNull(MDC.get("traceId"), "MDC limpo ao final");

        MockHttpServletRequest bad = new MockHttpServletRequest("GET", "/x");
        bad.addHeader("X-Trace-Id", "x y");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        f.doFilter(bad, res2, new MockFilterChain());
        assertTrue(res2.getHeader("X-Trace-Id").matches("[0-9a-f-]{36}"));
    }
}
