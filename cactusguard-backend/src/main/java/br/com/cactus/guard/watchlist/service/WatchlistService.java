package br.com.cactus.guard.watchlist.service;

import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.watchlist.entity.WatchlistEntry;
import br.com.cactus.guard.watchlist.repository.WatchlistEntryRepository;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistEntryRepository repository;
    private final UserRepository userRepository;

    @Transactional
    public WatchlistEntry add(String document, String name, String reason, String severity, Instant expiresAt) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElse(null);

        WatchlistEntry entry = WatchlistEntry.builder()
                .document(document)
                .name(name)
                .reason(reason)
                .severity(severity != null ? severity : "HIGH")
                .addedBy(user)
                .expiresAt(expiresAt)
                .build();

        return repository.save(entry);
    }

    public Optional<WatchlistEntry> check(String document) {
        return repository.findByDocumentAndActiveTrue(document);
    }

    public List<WatchlistEntry> listActive() {
        return repository.findByActiveTrue();
    }

    @Transactional
    public WatchlistEntry deactivate(UUID id) {
        WatchlistEntry entry = repository.findById(id)
                .orElseThrow(() -> new BusinessException("Entrada não encontrada"));
        entry.setActive(false);
        return repository.save(entry);
    }
}
