package br.com.cactus.guard.securityevent.controller;

import br.com.cactus.guard.securityevent.dto.SecurityEventResponse;
import br.com.cactus.guard.securityevent.service.SecurityEventQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/** Eventos de seguranca (tentativas de login). Exclusivo de PLATFORM_ADMIN. */
@Tag(name = "Plataforma - seguranca")
@RestController
@RequestMapping("/api/v1/platform/security-events")
@RequiredArgsConstructor
public class SecurityEventController {

    private final SecurityEventQueryService service;

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Lista eventos de seguranca (paginado; filtros: eventType, result, username, ip, tenantId, from, to)")
    public ResponseEntity<Page<SecurityEventResponse>> list(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 50, sort = "occurredAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(service.search(eventType, result, username, ip, tenantId, from, to, pageable));
    }
}
