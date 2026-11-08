package br.com.cactus.guard.device.push;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Consultas somente leitura em alert/case_ (sem editar esses pacotes), para escolher destinatarios. */
@Component
public class PushRecipientResolver {

    public record DueCase(UUID caseId, UUID assigneeId) {
    }

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public Optional<UUID> alertAssignee(UUID alertId) {
        List<UUID> r = em.createQuery(
                "select a.assignedTo.id from Alert a where a.id = :id and a.assignedTo is not null", UUID.class)
                .setParameter("id", alertId).getResultList();
        return r.stream().findFirst();
    }

    /** Cases em analise com prazo de analise ate {@code analysisLimit} ou aguardando comunicacao com prazo ate {@code commLimit}. */
    @Transactional(readOnly = true)
    public List<DueCase> dueCases(Instant analysisLimit, Instant commLimit, int max) {
        List<Object[]> rows = em.createQuery(
                "select c.id, a.id from Case c left join c.assignedTo a where "
                        + "(c.status in ('OPEN','IN_ANALYSIS','PENDING_DECISION','ESCALATED','PENDING_APPROVAL') "
                        + "  and c.analysisDeadline is not null and c.analysisDeadline <= :al) "
                        + "or (c.status = 'COMMUNICATE' and c.communicationDeadline is not null "
                        + "  and c.communicationDeadline <= :cl)", Object[].class)
                .setParameter("al", analysisLimit).setParameter("cl", commLimit)
                .setMaxResults(max).getResultList();
        return rows.stream().map(o -> new DueCase((UUID) o[0], (UUID) o[1])).toList();
    }
}
