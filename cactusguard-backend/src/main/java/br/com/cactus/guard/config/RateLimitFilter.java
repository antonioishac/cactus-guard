package br.com.cactus.guard.config;

import br.com.cactus.guard.config.ratelimit.RateLimiter;
import br.com.cactus.guard.config.ratelimit.RateLimiter.Decision;
import br.com.cactus.guard.tenant.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;

/**
 * Rate limit por ORIGEM, nunca global (um atacante nao consegue esgotar a cota de todos):
 * <ul>
 *   <li><b>Login</b> ({@code /auth/login}): por IP ({@code login-per-ip}, padrao 10/min). Complementa o bloqueio
 *       progressivo por usuario e por IP do {@code LoginGuard}; limite por username fica la (um limite por username
 *       aqui permitiria a um atacante travar o login de uma vitima).</li>
 *   <li><b>Busca</b> ({@code /search}): por usuario autenticado ({@code search-per-user}, padrao 30/min).</li>
 *   <li><b>API geral</b>: por usuario ({@code api-per-user}, 100/min), por tenant ({@code api-per-tenant}, 3000/min: um
 *       tenant barulhento nao degrada os demais) e por IP ({@code api-per-ip}, 1000/min, folgado por causa de NAT).</li>
 * </ul>
 * O IP vem de {@link ClientIpFilter} (so confia em {@code X-Forwarded-For} de proxy confiavel). Roda apos a cadeia do
 * Spring Security: usuario e tenant ja estao no contexto.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RateLimiter limiter;
    private final MeterRegistry meters;
    private final int loginPerIp;
    private final int searchPerUser;
    private final int apiPerUser;
    private final int apiPerTenant;
    private final int apiPerIp;

    public RateLimitFilter(RateLimiter limiter, MeterRegistry meters,
                           @Value("${cactus.ratelimit.login-per-ip:10}") int loginPerIp,
                           @Value("${cactus.ratelimit.search-per-user:30}") int searchPerUser,
                           @Value("${cactus.ratelimit.api-per-user:100}") int apiPerUser,
                           @Value("${cactus.ratelimit.api-per-tenant:3000}") int apiPerTenant,
                           @Value("${cactus.ratelimit.api-per-ip:1000}") int apiPerIp) {
        this.limiter = limiter;
        this.meters = meters;
        this.loginPerIp = loginPerIp;
        this.searchPerUser = searchPerUser;
        this.apiPerUser = apiPerUser;
        this.apiPerTenant = apiPerTenant;
        this.apiPerIp = apiPerIp;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String ip = ClientIpFilter.ipOf(request);

        Decision d;
        String scope;
        if (path.contains("/auth/login")) {
            scope = "login_ip";
            d = limiter.tryAcquire("login:ip:" + ip, loginPerIp, WINDOW);
        } else {
            String user = currentUser();
            String tenant = TenantContext.current().map(Object::toString).orElse(null);
            d = limiter.tryAcquire("api:ip:" + ip, apiPerIp, WINDOW);
            scope = "api_ip";
            if (d.allowed() && tenant != null) {
                d = limiter.tryAcquire("api:tenant:" + tenant, apiPerTenant, WINDOW);
                scope = "api_tenant";
            }
            if (d.allowed() && user != null) {
                boolean search = path.contains("/search");
                String key = (search ? "search:user:" : "api:user:") + (tenant == null ? "-" : tenant) + ":" + user;
                d = limiter.tryAcquire(key, search ? searchPerUser : apiPerUser, WINDOW);
                scope = search ? "search_user" : "api_user";
            }
        }

        if (d.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }
        meters.counter("cactus.ratelimit.rejected", "scope", scope).increment();
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(d.retryAfterSeconds()));
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":429,\"error\":\"RATE_LIMIT_EXCEEDED\",\"code\":\"RATE_LIMITED\","
                + "\"message\":\"Limite de requisições excedido.\",\"traceId\":\"" + safe(MDC.get("traceId")) + "\"}");
    }

    private static String currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || "anonymousUser".equals(a.getPrincipal())) {
            return null;
        }
        return a.getName() == null ? null : a.getName().toLowerCase(Locale.ROOT);
    }

    private static String safe(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9._-]", "");
    }
}
