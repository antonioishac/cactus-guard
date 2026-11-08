package br.com.cactus.guard.rule.repository;

import br.com.cactus.guard.rule.entity.MonitoringRuleTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MonitoringRuleTemplateRepository extends JpaRepository<MonitoringRuleTemplate, UUID> {
    List<MonitoringRuleTemplate> findAllByOrderByCodeAsc();
}
