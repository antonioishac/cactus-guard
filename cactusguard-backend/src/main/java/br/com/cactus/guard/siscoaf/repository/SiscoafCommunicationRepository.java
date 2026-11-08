package br.com.cactus.guard.siscoaf.repository;

import br.com.cactus.guard.siscoaf.entity.SiscoafCommunication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SiscoafCommunicationRepository extends JpaRepository<SiscoafCommunication, UUID> {
    Page<SiscoafCommunication> findByStatus(String status, Pageable pageable);
    Page<SiscoafCommunication> findByCaseEntityId(UUID caseId, Pageable pageable);

    List<SiscoafCommunication> findByCaseEntityIdAndType(UUID caseId, String type);

    boolean existsByTransactionIdAndTypeAndStatusNotIn(UUID transactionId, String type, Collection<String> statuses);

    boolean existsByTypeAndReferenceYearAndStatusNotIn(String type, Integer referenceYear, Collection<String> statuses);

    /** Comunicacoes de ocorrencia (COS/COE/legados) efetivamente enviadas no intervalo. */
    @Query("""
           select count(c) from SiscoafCommunication c
            where c.type in ('COS', 'COE', 'SAR', 'COAF_COMMUNICATION')
              and c.status in ('SUBMITTED', 'ACKNOWLEDGED')
              and c.submittedAt >= :from and c.submittedAt < :to
           """)
    long countSentBetween(@Param("from") Instant from, @Param("to") Instant to);
}
