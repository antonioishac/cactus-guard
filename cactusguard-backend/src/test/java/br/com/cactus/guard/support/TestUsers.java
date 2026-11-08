package br.com.cactus.guard.support;

import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.UUID;

/** Apoio para testes de quatro olhos: usuarios reais no banco e troca de usuario autenticado. */
public final class TestUsers {

    private TestUsers() {
    }

    public static User ensure(UserRepository repo, String username) {
        return repo.findByUsername(username).orElseGet(() -> repo.save(User.builder()
                .username(username)
                .email(username + "@test.local")
                .password("x")
                .name(username)
                .active(true)
                .build()));
    }

    /** Nome unico para evitar colisao entre classes de teste que compartilham o H2. */
    public static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static void login(String username, String... roles) {
        var authorities = Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(username, "x", authorities));
    }

    public static void logout() {
        SecurityContextHolder.clearContext();
    }
}
