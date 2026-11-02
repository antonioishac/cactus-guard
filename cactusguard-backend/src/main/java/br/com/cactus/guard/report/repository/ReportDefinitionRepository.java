package br.com.cactus.guard.report.repository;

import br.com.cactus.guard.report.entity.ReportDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, UUID> {
    Optional<ReportDefinition> findByCode(String code);
    List<ReportDefinition> findByActiveTrue();
}
