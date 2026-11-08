package br.com.cactus.guard.case_.repository;

import br.com.cactus.guard.case_.entity.CaseNote;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CaseNoteRepository extends JpaRepository<CaseNote, UUID> {
    @EntityGraph(attributePaths = "author")
    List<CaseNote> findByCaseEntityIdOrderByCreatedAtAsc(UUID caseId);
}
