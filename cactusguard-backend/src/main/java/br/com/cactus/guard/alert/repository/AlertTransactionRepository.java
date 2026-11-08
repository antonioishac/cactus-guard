package br.com.cactus.guard.alert.repository;

import br.com.cactus.guard.alert.entity.AlertTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AlertTransactionRepository extends JpaRepository<AlertTransaction, UUID> {
    List<AlertTransaction> findByAlertId(UUID alertId);
    List<AlertTransaction> findByTransactionId(UUID transactionId);
    boolean existsByAlertIdAndTransactionId(UUID alertId, UUID transactionId);
}
