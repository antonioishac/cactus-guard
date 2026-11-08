package br.com.cactus.guard.case_.repository;

import br.com.cactus.guard.transaction.entity.Transaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Consultas de leitura do dossie (limitadas) que nao pertencem a nenhum repositorio de outro modulo. */
@Repository
public class CaseDossierQueryRepository {

    @PersistenceContext
    private EntityManager em;

    /** Transacoes da parte na janela, mais recentes primeiro, limitadas a {@code max}. */
    public List<Transaction> transactionsInWindow(UUID partyId, Instant from, Instant to, int max) {
        return em.createQuery(
                        "SELECT t FROM Transaction t WHERE t.party.id = :partyId "
                                + "AND t.transactionDate >= :from AND t.transactionDate <= :to "
                                + "ORDER BY t.transactionDate DESC", Transaction.class)
                .setParameter("partyId", partyId)
                .setParameter("from", from)
                .setParameter("to", to)
                .setMaxResults(max)
                .getResultList();
    }
}
