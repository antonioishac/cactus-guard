package br.com.cactus.guard.rule.repository;

import br.com.cactus.guard.rule.entity.MonitoringRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MonitoringRuleRepository
        extends JpaRepository<MonitoringRule, UUID>, JpaSpecificationExecutor<MonitoringRule> {
    Optional<MonitoringRule> findByCode(String code);
    List<MonitoringRule> findByActiveTrue();
    long countByActiveTrue();
    List<MonitoringRule> findByCategoryAndActiveTrue(String category);
    long countByStatus(String status);

    @Override
    @EntityGraph(attributePaths = {"createdBy", "approvedBy"})
    Page<MonitoringRule> findAll(Specification<MonitoringRule> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"createdBy", "approvedBy"})
    List<MonitoringRule> findByStatusOrderByCodeAsc(String status);
}
