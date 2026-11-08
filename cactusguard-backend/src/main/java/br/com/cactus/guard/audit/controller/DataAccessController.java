package br.com.cactus.guard.audit.controller;

import br.com.cactus.guard.audit.dto.AuditFilter;
import br.com.cactus.guard.audit.dto.DataAccessLogResponse;
import br.com.cactus.guard.audit.service.DataAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Acesso a dados (LGPD)")
@RestController
@RequestMapping("/api/v1/data-access")
@RequiredArgsConstructor
public class DataAccessController {

    private final DataAccessService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('AUDITOR','ADMIN','SUPERVISOR')")
    @Operation(summary = "Log de acesso a dados paginado; filtros: userId|username, entityType, entityId, from, to")
    public ResponseEntity<Page<DataAccessLogResponse>> search(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Pageable pageable) {
        AuditFilter filter = new AuditFilter(userId, username, null, entityType, entityId,
                AuditFilter.parse(from, false), AuditFilter.parse(to, true));
        return ResponseEntity.ok(service.searchResponses(filter, pageable));
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    @PreAuthorize("hasAnyRole('AUDITOR','ADMIN','SUPERVISOR')")
    public ResponseEntity<Page<DataAccessLogResponse>> byEntity(
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            Pageable pageable) {
        return ResponseEntity.ok(service.searchResponses(
                new AuditFilter(null, null, null, entityType, entityId, null, null), pageable));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('AUDITOR','ADMIN','SUPERVISOR')")
    public ResponseEntity<Page<DataAccessLogResponse>> byUser(@PathVariable UUID userId, Pageable pageable) {
        return ResponseEntity.ok(service.searchResponses(
                new AuditFilter(userId, null, null, null, null, null, null), pageable));
    }
}
