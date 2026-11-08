package br.com.cactus.guard.party.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import br.com.cactus.guard.party.entity.Party;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartyRepository extends JpaRepository<Party, UUID>, JpaSpecificationExecutor<Party> {
    Optional<Party> findByDocumentHash(String documentHash);
    boolean existsByDocumentHash(String documentHash);

    List<Party> findByDocumentHashIn(Collection<String> documentHashes);

    /** PEP ativos cuja vigencia (fim do cargo + 5 anos) ja terminou. */
    List<Party> findByPepStatusAndPepExpiryDateBefore(String pepStatus, LocalDate date);

    Page<Party> findByIdIn(Collection<UUID> ids, Pageable pageable);

    // Nativa: o Hibernate NAO injeta @TenantId aqui; o filtro de tenant e explicito (e o RLS repete no banco).
    @Query(value = "SELECT * FROM parties WHERE tenant_id = :#{T(br.com.cactus.guard.tenant.TenantContext).currentOrNone()} AND search_vector @@ plainto_tsquery('portuguese', :query) ORDER BY ts_rank(search_vector, plainto_tsquery('portuguese', :query)) DESC",
           nativeQuery = true)
    Page<Party> searchFullText(@Param("query") String query, Pageable pageable);

    /**
     * Ids que casam com a busca full-text (tsvector + GIN), por relevancia, limitados. Usado por
     * GET /parties?q= combinado com os demais filtros (Specification) sem depender de ordenacao nativa.
     */
    @Query(value = "SELECT id FROM parties WHERE tenant_id = :#{T(br.com.cactus.guard.tenant.TenantContext).currentOrNone()} AND search_vector @@ plainto_tsquery('portuguese', :query) "
            + "ORDER BY ts_rank(search_vector, plainto_tsquery('portuguese', :query)) DESC LIMIT :max",
            nativeQuery = true)
    List<UUID> searchIdsFullText(@Param("query") String query, @Param("max") int max);
}
