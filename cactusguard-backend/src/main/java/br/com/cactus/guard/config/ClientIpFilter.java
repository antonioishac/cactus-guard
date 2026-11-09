package br.com.cactus.guard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Resolve o IP do cliente uma vez por requisicao (ver {@link ClientIpResolver}) e o expoe como atributo, para o
 * limitador, o bloqueio de login e os registros de auditoria/acesso a PII usarem o MESMO valor. Roda antes da cadeia do
 * Spring Security.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class ClientIpFilter extends OncePerRequestFilter {

    private final ClientIpResolver resolver;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        request.setAttribute(ClientIpResolver.ATTRIBUTE, resolver.resolve(request));
        chain.doFilter(request, response);
    }

    /** IP resolvido pelo filtro; sem ele (ex.: teste unitario), o IP do socket. */
    public static String ipOf(HttpServletRequest request) {
        Object v = request.getAttribute(ClientIpResolver.ATTRIBUTE);
        return v instanceof String s ? s : request.getRemoteAddr();
    }
}
