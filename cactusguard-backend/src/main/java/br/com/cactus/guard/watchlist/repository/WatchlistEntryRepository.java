package br.com.cactus.guard.watchlist.repository;

import br.com.cactus.guard.watchlist.entity.WatchlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WatchlistEntryRepository extends JpaRepository<WatchlistEntry, UUID> {
    Optional<WatchlistEntry> findByDocumentAndActiveTrue(String document);
    List<WatchlistEntry> findByActiveTrue();
}
