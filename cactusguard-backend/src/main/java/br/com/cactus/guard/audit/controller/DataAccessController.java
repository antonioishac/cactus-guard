package br.com.cactus.guard.audit.controller;

import br.com.cactus.guard.audit.entity.DataAccessLog;
import br.com.cactus.guard.audit.service.DataAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/data-access")
@RequiredArgsConstructor
public class DataAccessController {

    private final DataAccessService service;

    @GetMapping("/entity/{entityType}/{entityId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<DataAccessLog>> byEntity(
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            Pageable pageable) {
        return ResponseEntity.ok(service.findByEntity(entityType, entityId, pageable));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<DataAccessLog>> byUser(@PathVariable UUID userId, Pageable pageable) {
        return ResponseEntity.ok(service.findByUser(userId, pageable));
    }
}
