package br.com.cactus.guard.audit.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import br.com.cactus.guard.audit.dto.AuditFilter;
import br.com.cactus.guard.audit.dto.AuditLogResponse;
import br.com.cactus.guard.audit.dto.AuditIntegrityResponse;
import br.com.cactus.guard.audit.service.AuditIntegrityService;
import br.com.cactus.guard.audit.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Auditoria")
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final AuditIntegrityService integrityService;

    @GetMapping
    @PreAuthorize("hasAnyRole('AUDITOR','ADMIN','SUPERVISOR')")
    @Operation(summary = "Trilha de auditoria paginada; filtros: userId|username, action, entityType, entityId, from, to (yyyy-MM-dd ou ISO-8601)")
    public ResponseEntity<Page<AuditLogResponse>> search(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Pageable pageable) {
        AuditFilter filter = new AuditFilter(userId, username, action, entityType, entityId,
                AuditFilter.parse(from, false), AuditFilter.parse(to, true));
        return ResponseEntity.ok(auditService.search(filter, pageable));
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    @PreAuthorize("hasAnyRole('AUDITOR','ADMIN','SUPERVISOR')")
    public ResponseEntity<Page<AuditLogResponse>> findByEntity(
            @PathVariable String entityType,
            @PathVariable UUID entityId,
            Pageable pageable) {
        AuditFilter filter = new AuditFilter(null, null, null, entityType, entityId, null, null);
        return ResponseEntity.ok(auditService.search(filter, pageable));
    }

    @GetMapping("/integrity")
    @PreAuthorize("hasAnyRole('AUDITOR','ADMIN','SUPERVISOR')")
    @Operation(summary = "Verifica a cadeia de hash da trilha de auditoria do tenant (recalcula no banco); "
            + "retorna ok ou o primeiro registro divergente. Registros anteriores a V45 nao sao encadeados.")
    public ResponseEntity<AuditIntegrityResponse> integrity() {
        return ResponseEntity.ok(integrityService.verify());
    }
}
