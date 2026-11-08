package br.com.cactus.guard.party.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.cactus.guard.party.entity.Party;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface PartyRepository extends JpaRepository<Party, UUID> {
    Optional<Party> findByDocumentHash(String documentHash);
    boolean existsByDocumentHash(String documentHash);

    @Query(value = "SELECT * FROM parties WHERE search_vector @@ plainto_tsquery('portuguese', :query) ORDER BY ts_rank(search_vector, plainto_tsquery('portuguese', :query)) DESC", 
           nativeQuery = true)
    Page<Party> searchFullText(@Param("query") String query, Pageable pageable);
}
