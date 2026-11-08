package br.com.cactus.guard.case_.controller;

import br.com.cactus.guard.case_.dto.CaseApprovalRequest;
import br.com.cactus.guard.case_.dto.CaseDecisionRequest;
import br.com.cactus.guard.case_.dto.CaseEscalationRequest;
import br.com.cactus.guard.case_.dto.CaseResponse;
import br.com.cactus.guard.case_.service.CaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Cases")
@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;

    @PostMapping("/from-alert/{alertId}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Seleciona o alerta e abre o case (inicia os 45 dias de analise)")
    public ResponseEntity<CaseResponse> createFromAlert(@PathVariable UUID alertId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(caseService.createFromAlert(alertId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Lista cases (paginado)")
    public ResponseEntity<Page<CaseResponse>> findAll(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(caseService.findAll(status, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Detalha um case")
    public ResponseEntity<CaseResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(caseService.findById(id));
    }

    @PostMapping("/{id}/decide")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Decisao humana: COMMUNICATE ou ARCHIVE (justificativa obrigatoria)")
    public ResponseEntity<CaseResponse> decide(
            @PathVariable UUID id,
            @Valid @RequestBody CaseDecisionRequest request) {
        return ResponseEntity.ok(caseService.decide(id, request));
    }

    @PostMapping("/{id}/escalate")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Escalona o case (nao e decisao final; justificativa obrigatoria)")
    public ResponseEntity<CaseResponse> escalate(
            @PathVariable UUID id,
            @Valid @RequestBody CaseEscalationRequest request) {
        return ResponseEntity.ok(caseService.escalate(id, request));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPERVISOR')")
    @Operation(summary = "Aprovacao quatro olhos (SUPERVISOR diferente de quem decidiu)")
    public ResponseEntity<CaseResponse> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) CaseApprovalRequest request) {
        return ResponseEntity.ok(caseService.approve(id, request));
    }
}
