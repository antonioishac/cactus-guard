package br.com.cactus.guard.transaction.repository;

import br.com.cactus.guard.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Consultas do motor de regras sao agregadas por janela (uma consulta por regra, nao por parte).
 * O parametro {@code partyId} e opcional: nulo = todas as partes (batch); preenchido = so aquela parte
 * (monitoramento por evento, spec 047/048).
 * O valor de referencia e {@code coalesce(amountBrl, amount)} (amount_brl quando informado).
 */
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByPartyId(UUID partyId, Pageable pageable);

    List<Transaction> findByPartyIdAndTransactionDateAfter(UUID partyId, Instant since);

    @Query("SELECT t FROM Transaction t WHERE t.party.id = :partyId AND t.amount >= :minAmount AND t.transactionDate >= :since")
    List<Transaction> findHighValueByParty(@Param("partyId") UUID partyId,
                                           @Param("minAmount") BigDecimal minAmount,
                                           @Param("since") Instant since);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.party.id = :partyId AND t.transactionDate >= :since")
    long countByPartySince(@Param("partyId") UUID partyId, @Param("since") Instant since);

    // ---------------------------------------------------------------- consultas agregadas

    /** Transacoes de valor ao menos {@code min} na janela (qualquer parte). */
    @Query("""
           SELECT t FROM Transaction t JOIN FETCH t.party
            WHERE t.transactionDate >= :since AND COALESCE(t.amountBrl, t.amount) >= :min AND (:partyId IS NULL OR t.party.id = :partyId)
            ORDER BY t.transactionDate
           """)
    List<Transaction> findAtLeastSince(@Param("min") BigDecimal min, @Param("since") Instant since,
                                       @Param("partyId") UUID partyId);

    /** Transacoes na faixa [min, max) na janela. */
    @Query("""
           SELECT t FROM Transaction t JOIN FETCH t.party
            WHERE t.transactionDate >= :since
              AND COALESCE(t.amountBrl, t.amount) >= :min AND COALESCE(t.amountBrl, t.amount) < :max AND (:partyId IS NULL OR t.party.id = :partyId)
            ORDER BY t.transactionDate
           """)
    List<Transaction> findInBandSince(@Param("min") BigDecimal min, @Param("max") BigDecimal max,
                                      @Param("since") Instant since, @Param("partyId") UUID partyId);

    /** Partes com ao menos {@code minCount} transacoes na janela: [partyId, count]. */
    @Query("""
           SELECT t.party.id, COUNT(t) FROM Transaction t
            WHERE t.transactionDate >= :since AND (:partyId IS NULL OR t.party.id = :partyId)
            GROUP BY t.party.id HAVING COUNT(t) >= :minCount
           """)
    List<Object[]> countByPartySinceHaving(@Param("since") Instant since, @Param("minCount") long minCount,
                                           @Param("partyId") UUID partyId);

    /** Media por parte na janela, para partes com ao menos {@code minCount}: [partyId, count, avg]. */
    @Query("""
           SELECT t.party.id, COUNT(t), AVG(COALESCE(t.amountBrl, t.amount)) FROM Transaction t
            WHERE t.transactionDate >= :since AND (:partyId IS NULL OR t.party.id = :partyId)
            GROUP BY t.party.id HAVING COUNT(t) >= :minCount
           """)
    List<Object[]> averageByParty(@Param("since") Instant since, @Param("minCount") long minCount,
                                  @Param("partyId") UUID partyId);

    /** Volume por parte na janela: [partyId, sum]. */
    @Query("""
           SELECT t.party.id, SUM(COALESCE(t.amountBrl, t.amount)) FROM Transaction t
            WHERE t.transactionDate >= :since AND (:partyId IS NULL OR t.party.id = :partyId)
            GROUP BY t.party.id HAVING SUM(COALESCE(t.amountBrl, t.amount)) >= :minSum
           """)
    List<Object[]> volumeByParty(@Param("since") Instant since, @Param("minSum") BigDecimal minSum,
                                 @Param("partyId") UUID partyId);

    /** Volume por parte (subconjunto de partes) na janela: [partyId, sum]. */
    @Query("""
           SELECT t.party.id, SUM(COALESCE(t.amountBrl, t.amount)) FROM Transaction t
            WHERE t.party.id IN :ids AND t.transactionDate >= :since
            GROUP BY t.party.id
           """)
    List<Object[]> volumeByPartyIn(@Param("ids") Collection<UUID> ids, @Param("since") Instant since);

    /** Transacoes de um conjunto de partes na janela (carregadas de uma vez). */
    @Query("""
           SELECT t FROM Transaction t JOIN FETCH t.party
            WHERE t.party.id IN :ids AND t.transactionDate >= :since
            ORDER BY t.transactionDate
           """)
    List<Transaction> findByPartiesSince(@Param("ids") Collection<UUID> ids, @Param("since") Instant since);

    /** Partes com entrada (IN) de valor relevante na janela. */
    @Query("""
           SELECT DISTINCT t.party.id FROM Transaction t
            WHERE t.direction = 'IN' AND t.transactionDate >= :since
              AND COALESCE(t.amountBrl, t.amount) >= :min AND (:partyId IS NULL OR t.party.id = :partyId)
           """)
    List<UUID> findPartyIdsWithInflowSince(@Param("min") BigDecimal min, @Param("since") Instant since,
                                           @Param("partyId") UUID partyId);

    /** Transacoes de partes PEP acima do limiar na janela. */
    @Query("""
           SELECT t FROM Transaction t JOIN FETCH t.party p
            WHERE p.pep = true AND p.pepStatus <> 'EXPIRED'
              AND t.transactionDate >= :since AND COALESCE(t.amountBrl, t.amount) >= :min AND (:partyId IS NULL OR t.party.id = :partyId)
            ORDER BY t.transactionDate
           """)
    List<Transaction> findPepTransactionsSince(@Param("min") BigDecimal min, @Param("since") Instant since,
                                               @Param("partyId") UUID partyId);

    /** Transacoes cuja origem, destino ou contraparte estao entre os paises informados. */
    @Query("""
           SELECT t FROM Transaction t JOIN FETCH t.party
            WHERE t.transactionDate >= :since AND COALESCE(t.amountBrl, t.amount) >= :min
              AND (t.originCountry IN :countries OR t.destinationCountry IN :countries
                   OR t.counterpartyCountry IN :countries) AND (:partyId IS NULL OR t.party.id = :partyId)
            ORDER BY t.transactionDate
           """)
    List<Transaction> findByCountriesSince(@Param("countries") Collection<String> countries,
                                           @Param("min") BigDecimal min, @Param("since") Instant since,
                                           @Param("partyId") UUID partyId);

    /** Partes (subconjunto) com movimentacao envolvendo paises de alto risco na janela. */
    @Query("""
           SELECT DISTINCT t.party.id FROM Transaction t
            WHERE t.party.id IN :ids AND t.transactionDate >= :since
              AND (t.originCountry IN :countries OR t.destinationCountry IN :countries
                   OR t.counterpartyCountry IN :countries)
           """)
    List<UUID> findPartyIdsWithCountriesSince(@Param("ids") Collection<UUID> ids,
                                              @Param("countries") Collection<String> countries,
                                              @Param("since") Instant since);

    /** Operacoes em especie (ou provisionamento de saque) acima do limiar sem COE vigente. */
    @Query("""
           SELECT t FROM Transaction t JOIN FETCH t.party
            WHERE (t.cash = true OR t.cashWithdrawalProvision = true)
              AND t.transactionDate >= :since
              AND COALESCE(t.amountBrl, t.amount) >= :min
              AND (t.amountBrl IS NOT NULL OR t.currency = 'BRL')
              AND NOT EXISTS (SELECT 1 FROM SiscoafCommunication c
                               WHERE c.transaction = t AND c.type = 'COE'
                                 AND c.status NOT IN ('CANCELLED', 'RECTIFIED', 'REJECTED'))
           """)
    List<Transaction> findCashWithoutCoe(@Param("min") BigDecimal min, @Param("since") Instant since);
}
