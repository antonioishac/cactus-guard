package br.com.cactus.guard.audit.controller;

import br.com.cactus.guard.audit.entity.AuditLog;
import br.com.cactus.guard.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/entity/{entityType}/{entityId}")
    public ResponseEntity<Page<AuditLog>> findByEntity(
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            Pageable pageable) {
        return ResponseEntity.ok(auditService.findByEntity(entityType, entityId, pageable));
    }
}
