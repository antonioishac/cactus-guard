package br.com.cactus.guard.tenant.repository;

import br.com.cactus.guard.tenant.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findByCode(String code);
    boolean existsByCode(String code);
    boolean existsByCnpjHash(String cnpjHash);
    List<Tenant> findByStatusOrderByCodeAsc(String status);
}
