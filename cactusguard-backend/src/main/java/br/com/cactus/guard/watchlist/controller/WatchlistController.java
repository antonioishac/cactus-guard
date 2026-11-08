package br.com.cactus.guard.watchlist.controller;

import br.com.cactus.guard.watchlist.entity.WatchlistEntry;
import br.com.cactus.guard.watchlist.service.WatchlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService service;

    @PostMapping
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<WatchlistEntry> add(
            @RequestParam String document,
            @RequestParam String name,
            @RequestParam String reason,
            @RequestParam(defaultValue = "HIGH") String severity,
            @RequestParam(required = false) Instant expiresAt) {
        return ResponseEntity.ok(service.add(document, name, reason, severity, expiresAt));
    }

    @GetMapping("/check/{document}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<WatchlistEntry> check(@PathVariable String document) {
        return service.check(document)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AUDITOR','ADMIN','SUPERVISOR')")
    public ResponseEntity<List<WatchlistEntry>> list() {
        return ResponseEntity.ok(service.listActive());
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<WatchlistEntry> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(service.deactivate(id));
    }
}
