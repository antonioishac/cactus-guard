package br.com.cactus.guard.alert.controller;

import br.com.cactus.guard.alert.dto.AlertCommentRequest;
import br.com.cactus.guard.alert.dto.AlertCommentResponse;
import br.com.cactus.guard.alert.dto.AlertDetailResponse;
import br.com.cactus.guard.alert.dto.AlertEscalationRequest;
import br.com.cactus.guard.alert.dto.AlertResponse;
import br.com.cactus.guard.alert.dto.FalsePositiveRequest;
import br.com.cactus.guard.alert.repository.AlertSpecifications;
import br.com.cactus.guard.alert.service.AlertService;
import br.com.cactus.guard.common.util.DateRange;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Alertas")
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Lista alertas (paginado). status/severity aceitam varios valores (repetidos ou separados por virgula)")
    public ResponseEntity<Page<AlertResponse>> findAll(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> severity,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) UUID partyId,
            @RequestParam(required = false) String ruleCode,
            Pageable pageable) {
        DateRange range = DateRange.of(from, to);
        var filter = new AlertSpecifications.Filter(status, severity, type, assignedTo,
                range.from(), range.toExclusive(), partyId, ruleCode);
        return ResponseEntity.ok(alertService.findAll(filter, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Detalha um alerta (evidencias, transacoes vinculadas, historico, regra e resumo da parte)")
    public ResponseEntity<AlertDetailResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(alertService.findDetail(id));
    }

    @PostMapping("/{id}/assume")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Assume o alerta para analise")
    public ResponseEntity<AlertResponse> assume(@PathVariable UUID id) {
        return ResponseEntity.ok(alertService.assume(id));
    }

    @PostMapping("/{id}/escalate")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Escala o alerta (nao e decisao; justificativa obrigatoria)")
    public ResponseEntity<AlertResponse> escalate(@PathVariable UUID id,
                                                  @Valid @RequestBody AlertEscalationRequest request) {
        return ResponseEntity.ok(alertService.escalate(id, request.justification()));
    }

    @GetMapping("/{id}/comments")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Comentarios do alerta (autor e data)")
    public ResponseEntity<List<AlertCommentResponse>> comments(@PathVariable UUID id) {
        return ResponseEntity.ok(alertService.listComments(id));
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Comenta o alerta (texto obrigatorio; nao altera o status; auditado)")
    public ResponseEntity<AlertCommentResponse> addComment(@PathVariable UUID id,
                                                           @Valid @RequestBody AlertCommentRequest request) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(alertService.addComment(id, request.text()));
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
