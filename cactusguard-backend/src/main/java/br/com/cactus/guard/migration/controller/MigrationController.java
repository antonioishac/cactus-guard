package br.com.cactus.guard.migration.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import br.com.cactus.guard.migration.entity.MigrationBatch;
import br.com.cactus.guard.migration.service.MigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/migration")
@RequiredArgsConstructor
public class MigrationController {

    private final MigrationService migrationService;

    @PostMapping("/batches")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MigrationBatch> createBatch(@RequestParam String sourceSystem) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(migrationService.createBatch(sourceSystem));
    }

    @PostMapping("/batches/{batchId}/records")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> addRecord(
            @PathVariable UUID batchId,
            @RequestParam String externalId,
            @RequestParam String document,
            @RequestBody(required = false) Map<String, Object> rawData) {
        migrationService.addRecord(batchId, externalId, document, rawData);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/batches/{batchId}/process")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> processBatch(@PathVariable UUID batchId) {
        int processed = migrationService.processBatch(batchId);
        return ResponseEntity.ok(Map.of("processed", processed));
    }
}
