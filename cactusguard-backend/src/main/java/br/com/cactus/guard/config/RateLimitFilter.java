package br.com.cactus.guard.config;

import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> rateLimitBuckets;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        Bucket bucket = resolveBucket(path);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":429,\"error\":\"RATE_LIMIT_EXCEEDED\",\"code\":\"RATE_LIMITED\",\"message\":\"Limite de requisições excedido.\",\"traceId\":\"" + org.slf4j.MDC.get("traceId") + "\"}");
        }
    }

    private Bucket resolveBucket(String path) {
        if (path.contains("/auth/login")) return rateLimitBuckets.get("login");
        if (path.contains("/search")) return rateLimitBuckets.get("search");
        return rateLimitBuckets.get("default");
    }
}
