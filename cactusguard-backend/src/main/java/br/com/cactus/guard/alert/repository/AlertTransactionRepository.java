package br.com.cactus.guard.alert.repository;

import br.com.cactus.guard.alert.entity.AlertTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface AlertTransactionRepository extends JpaRepository<AlertTransaction, UUID> {
    List<AlertTransaction> findByAlertId(UUID alertId);
    List<AlertTransaction> findByTransactionId(UUID transactionId);
    boolean existsByAlertIdAndTransactionId(UUID alertId, UUID transactionId);

    /** Vinculos com a transacao carregada, em ordem cronologica. */
    @Query("SELECT at FROM AlertTransaction at JOIN FETCH at.transaction t "
            + "WHERE at.alert.id = :alertId ORDER BY t.transactionDate")
    List<AlertTransaction> findWithTransactionByAlertId(@Param("alertId") UUID alertId);
}
