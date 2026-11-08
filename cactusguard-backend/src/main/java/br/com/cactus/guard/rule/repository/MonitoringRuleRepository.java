package br.com.cactus.guard.rule.repository;

import br.com.cactus.guard.rule.entity.MonitoringRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MonitoringRuleRepository extends JpaRepository<MonitoringRule, UUID> {
    Optional<MonitoringRule> findByCode(String code);
    List<MonitoringRule> findByActiveTrue();
    long countByActiveTrue();
    List<MonitoringRule> findByCategoryAndActiveTrue(String category);
}
