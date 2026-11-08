package br.com.cactus.guard.alert.repository;

import br.com.cactus.guard.alert.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID>, JpaSpecificationExecutor<Alert> {

    Page<Alert> findByStatus(String status, Pageable pageable);

    Page<Alert> findBySeverity(String severity, Pageable pageable);

    /** Listagem filtrada com parte e responsavel carregados (evita N+1 no mapeamento). */
    @Override
    @EntityGraph(attributePaths = {"party", "assignedTo"})
    Page<Alert> findAll(Specification<Alert> spec, Pageable pageable);

    @Query("SELECT a FROM Alert a WHERE a.party.id = :partyId")
    Page<Alert> findByPartyId(@Param("partyId") UUID partyId, Pageable pageable);

    boolean existsByDedupKey(String dedupKey);

    /** Chaves ja existentes (qualquer status) dentre as informadas: evita N consultas por parte. */
    @Query("SELECT a.dedupKey FROM Alert a WHERE a.dedupKey IN :keys")
    List<String> findExistingDedupKeys(@Param("keys") Collection<String> keys);

    long countByPartyIdAndRuleCodeAndStatus(UUID partyId, String ruleCode, String status);

    long countByStatus(String status);
    long countBySeverityAndStatus(String severity, String status);

    // ---------------------------------------------------------------- agregacoes (dashboard / metricas)

    @Query("SELECT a.status, COUNT(a) FROM Alert a GROUP BY a.status")
    List<Object[]> countGroupedByStatus();

    @Query("SELECT a.severity, COUNT(a) FROM Alert a WHERE a.status IN :statuses GROUP BY a.severity")
    List<Object[]> countGroupedBySeverity(@Param("statuses") Collection<String> statuses);

    /** Alertas criados por dia (data do servidor) desde o instante informado: [LocalDate, count]. */
    @Query("SELECT CAST(a.createdAt AS LocalDate), COUNT(a) FROM Alert a WHERE a.createdAt >= :since "
            + "GROUP BY CAST(a.createdAt AS LocalDate) ORDER BY CAST(a.createdAt AS LocalDate)")
    List<Object[]> countCreatedPerDaySince(@Param("since") Instant since);

    /** Alertas ainda nao selecionados (em tratamento e sem case) por prazo de selecao. */
    @Query("SELECT COUNT(a) FROM Alert a WHERE a.status IN :statuses AND NOT EXISTS "
            + "(SELECT 1 FROM br.com.cactus.guard.case_.entity.Case c WHERE c.alert = a)")
    long countUnselected(@Param("statuses") Collection<String> statuses);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.status IN :statuses AND a.selectionDeadline < :limit AND NOT EXISTS "
            + "(SELECT 1 FROM br.com.cactus.guard.case_.entity.Case c WHERE c.alert = a)")
    long countUnselectedOverdue(@Param("statuses") Collection<String> statuses, @Param("limit") Instant limit);

    @Query("SELECT COUNT(a) FROM Alert a WHERE a.status IN :statuses AND a.selectionDeadline >= :from "
            + "AND a.selectionDeadline < :to AND NOT EXISTS "
            + "(SELECT 1 FROM br.com.cactus.guard.case_.entity.Case c WHERE c.alert = a)")
    long countUnselectedDueBetween(@Param("statuses") Collection<String> statuses,
                                   @Param("from") Instant from, @Param("to") Instant to);

    /** Alertas das regras no periodo agrupados por regra e status: [ruleCode, status, count]. */
    @Query("SELECT a.ruleCode, a.status, COUNT(a) FROM Alert a WHERE a.ruleCode IN :codes "
            + "AND a.createdAt >= :from AND a.createdAt < :to GROUP BY a.ruleCode, a.status")
    List<Object[]> countByRuleAndStatus(@Param("codes") Collection<String> codes,
                                        @Param("from") Instant from, @Param("to") Instant to);
}
