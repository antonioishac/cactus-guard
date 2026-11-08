package br.com.cactus.guard.tenant.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Descobre o tenant de um username ANTES de existir tenant no contexto (login). No PostgreSQL usa a funcao
 * SECURITY DEFINER {@code app_find_user_tenant} (V38), unico ponto que enxerga usuarios de todos os tenants e
 * que devolve apenas o UUID do tenant; em H2 (testes) le a tabela diretamente.
 */
@Component
public class TenantDirectory {

    @PersistenceContext
    private EntityManager em;

    private volatile Boolean postgres;

    @Transactional(readOnly = true)
    public Optional<UUID> findTenantIdByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String sql = isPostgres()
                ? "SELECT app_find_user_tenant(:u)"
                : "SELECT tenant_id FROM users WHERE username = :u";
        UUID r = em.unwrap(org.hibernate.Session.class).createNativeQuery(sql, UUID.class)
                .setParameter("u", username).getResultList().stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
        return Optional.ofNullable(r);
    }

    private boolean isPostgres() {
        if (postgres == null) {
            postgres = em.unwrap(org.hibernate.Session.class)
                    .doReturningWork(c -> c.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres"));
        }
        return postgres;
    }
}
