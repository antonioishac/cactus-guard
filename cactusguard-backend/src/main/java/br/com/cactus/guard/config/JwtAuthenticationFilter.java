package br.com.cactus.guard.config;

import br.com.cactus.guard.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import br.com.cactus.guard.tenant.TenantContext;
import org.slf4j.MDC;
import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final br.com.cactus.guard.tenant.service.TenantAccessService tenantAccess;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        // token invalido/expirado/adulterado: segue sem autenticacao (-> 401), nunca 500
        final String username = jwtService.isTokenValid(jwt) ? jwtService.extractUsername(jwt) : null;

        boolean tenantBound = false;
        try {
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // Multi-tenancy: o tenant vem EXCLUSIVAMENTE do claim assinado; nunca de header/parametro.
                UUID tenantId = jwtService.extractTenantId(jwt).orElse(null);
                if (tenantId == null || !tenantAccess.isActive(tenantId)) {
                    denyTenant(response);
                    return;
                }
                TenantContext.set(tenantId);
                tenantBound = true;
                try {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    if (userDetails.isEnabled()) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                } catch (UsernameNotFoundException e) {
                    // usuario removido (ou de outro tenant): trata como nao autenticado
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            if (tenantBound) {
                TenantContext.clear();
            }
        }
    }

    /** Token valido, mas tenant ausente, inexistente ou suspenso: 403 (sem detalhar o motivo). */
    private void denyTenant(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Acesso negado\","
                + "\"code\":\"TENANT_INACTIVE\",\"traceId\":\"" + MDC.get("traceId") + "\"}");
    }
}
