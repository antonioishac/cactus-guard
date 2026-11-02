package br.com.cactus.guard.report.repository;

import br.com.cactus.guard.report.entity.ReportJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ReportJobRepository extends JpaRepository<ReportJob, UUID> {
    Page<ReportJob> findByRequestedById(UUID userId, Pageable pageable);
    Page<ReportJob> findByStatus(String status, Pageable pageable);
}
