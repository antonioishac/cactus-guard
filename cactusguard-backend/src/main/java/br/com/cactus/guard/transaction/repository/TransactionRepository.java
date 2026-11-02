package br.com.cactus.guard.transaction.repository;

import br.com.cactus.guard.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByPartyId(UUID partyId, Pageable pageable);

    List<Transaction> findByPartyIdAndOccurredAtAfter(UUID partyId, Instant since);

    @Query("SELECT t FROM Transaction t WHERE t.party.id = :partyId AND t.amount >= :minAmount AND t.transactionDate >= :since")
    List<Transaction> findHighValueByParty(@Param("partyId") UUID partyId,
                                           @Param("minAmount") BigDecimal minAmount,
                                           @Param("since") Instant since);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.party.id = :partyId AND t.transactionDate >= :since")
    long countByPartySince(@Param("partyId") UUID partyId, @Param("since") Instant since);
}
