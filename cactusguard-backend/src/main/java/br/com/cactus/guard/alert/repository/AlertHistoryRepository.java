package br.com.cactus.guard.alert.repository;

import br.com.cactus.guard.alert.entity.AlertHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AlertHistoryRepository extends JpaRepository<AlertHistory, UUID> {
    List<AlertHistory> findByAlertIdOrderByCreatedAtDesc(UUID alertId);
}
