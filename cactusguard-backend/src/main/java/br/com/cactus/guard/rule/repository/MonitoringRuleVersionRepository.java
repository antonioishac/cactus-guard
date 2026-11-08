package br.com.cactus.guard.rule.repository;

import br.com.cactus.guard.rule.entity.MonitoringRuleVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MonitoringRuleVersionRepository extends JpaRepository<MonitoringRuleVersion, UUID> {

    List<MonitoringRuleVersion> findByRuleIdOrderByVersionDesc(UUID ruleId);

    Optional<MonitoringRuleVersion> findByRuleIdAndVersion(UUID ruleId, int version);

    /** Versao vigente da regra em um instante (reconstrucao historica). */
    @Query("""
           select v from MonitoringRuleVersion v
            where v.rule.id = :ruleId
              and (v.effectiveFrom is null or v.effectiveFrom <= :at)
              and (v.effectiveTo is null or v.effectiveTo > :at)
            order by v.version desc
           """)
    List<MonitoringRuleVersion> findEffectiveAt(@Param("ruleId") UUID ruleId, @Param("at") Instant at);
}
