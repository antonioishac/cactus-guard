package br.com.cactus.guard.config;

import br.com.cactus.guard.config.ratelimit.LocalRateLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Limite de login POR IP (nunca global) e IP real so de proxy confiavel. */
class RateLimitFilterTest {

    private static final String LOGIN = "/api/v1/auth/login";

    private final LocalRateLimiter limiter = new LocalRateLimiter(1000);
    private final RateLimitFilter filter = new RateLimitFilter(limiter, new SimpleMeterRegistry(), 3, 30, 100, 3000, 1000);

    private static MockHttpServletRequest request(String remoteAddr, String xff) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", LOGIN);
        r.setRequestURI(LOGIN);
        r.setRemoteAddr(remoteAddr);
        if (xff != null) {
            r.addHeader("X-Forwarded-For", xff);
        }
        return r;
    }

    private int call(ClientIpFilter ipFilter, MockHttpServletRequest req) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        ipFilter.doFilter(req, res, new MockFilterChain(new jakarta.servlet.GenericServlet() {
            @Override
            public void service(jakarta.servlet.ServletRequest rq, jakarta.servlet.ServletResponse rs) {
                // 200
            }
        }, filter));
        return res.getStatus();
    }

    private static ClientIpFilter ipFilter(String trusted) {
        return new ClientIpFilter(new ClientIpResolver(trusted));
    }

    @Test
    void twoDistinctIpsDoNotBlockEachOther() throws Exception {
        ClientIpFilter ips = ipFilter("");
        for (int i = 0; i < 3; i++) {
            assertEquals(200, call(ips, request("203.0.113.10", null)));
        }
        assertEquals(429, call(ips, request("203.0.113.10", null)), "o 4o do mesmo IP e barrado");
        // outro IP segue livre: o limite nao e global
        for (int i = 0; i < 3; i++) {
            assertEquals(200, call(ips, request("203.0.113.11", null)));
        }
    }

    @Test
    void forgedForwardedForFromUntrustedPeerIsIgnored() throws Exception {
        ClientIpFilter ips = ipFilter("10.0.0.0/8");
        // atacante (203.0.113.50, nao e proxy) tenta trocar de "IP" a cada requisicao: continua sendo um unico IP
        for (int i = 0; i < 3; i++) {
            assertEquals(200, call(ips, request("203.0.113.50", "198.51.100." + i)));
        }
        assertEquals(429, call(ips, request("203.0.113.50", "198.51.100.99")));
    }

    @Test
    void forwardedForFromTrustedProxyIdentifiesTheRealClient() throws Exception {
        ClientIpFilter ips = ipFilter("10.0.0.0/8");
        for (int i = 0; i < 3; i++) {
            assertEquals(200, call(ips, request("10.1.2.3", "198.51.100.7")));
        }
        assertEquals(429, call(ips, request("10.1.2.3", "198.51.100.7")));
        // outro cliente atras do MESMO proxy nao e afetado
        assertEquals(200, call(ips, request("10.1.2.3", "198.51.100.8")));
    }

    @Test
    void resolverWalksRightToLeftSkippingTrustedHopsAndIgnoresGarbage() {
        ClientIpResolver r = new ClientIpResolver("10.0.0.0/8,192.168.0.1");
        // cliente forja o inicio da lista; o proxy confiavel acrescentou o IP real a direita
        assertEquals("198.51.100.7", r.resolve(request("10.0.0.1", "1.2.3.4, 198.51.100.7, 10.0.0.9")));
        // lixo no cabecalho: nao usa texto livre
        assertEquals("10.0.0.1", r.resolve(request("10.0.0.1", "evil<script>")));
        // par nao confiavel: cabecalho ignorado
        assertEquals("203.0.113.9", r.resolve(request("203.0.113.9", "1.1.1.1")));
        // sem proxies configurados: sempre o socket
        assertEquals("10.0.0.1", new ClientIpResolver("").resolve(request("10.0.0.1", "1.1.1.1")));
    }

    @Test
    void ipOfFallsBackToSocketWithoutFilter() {
        HttpServletRequest r = request("203.0.113.1", "9.9.9.9");
        assertNotNull(r);
        assertEquals("203.0.113.1", ClientIpFilter.ipOf(r));
    }
}
