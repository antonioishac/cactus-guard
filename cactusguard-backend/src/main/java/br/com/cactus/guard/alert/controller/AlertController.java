package br.com.cactus.guard.alert.controller;

import br.com.cactus.guard.alert.dto.AlertResponse;
import br.com.cactus.guard.alert.dto.FalsePositiveRequest;
import br.com.cactus.guard.alert.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Alertas")
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Lista alertas (paginado)")
    public ResponseEntity<Page<AlertResponse>> findAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            Pageable pageable) {
        return ResponseEntity.ok(alertService.findAll(status, severity, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Detalha um alerta")
    public ResponseEntity<AlertResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(alertService.findById(id));
    }

    @PostMapping("/{id}/assume")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Assume o alerta para analise")
    public ResponseEntity<AlertResponse> assume(@PathVariable UUID id) {
        return ResponseEntity.ok(alertService.assume(id));
    }

    @PostMapping("/{id}/false-positive")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Marca como falso positivo (motivo e justificativa obrigatorios)")
    public ResponseEntity<AlertResponse> falsePositive(
            @PathVariable UUID id,
            @Valid @RequestBody FalsePositiveRequest request) {
        return ResponseEntity.ok(alertService.markFalsePositive(id, request.reason(), request.justification()));
    }
}
