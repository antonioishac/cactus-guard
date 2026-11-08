package br.com.cactus.guard.approval.controller;

import br.com.cactus.guard.approval.dto.PendingApprovalsResponse;
import br.com.cactus.guard.approval.service.PendingApprovalsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Fila de aprovacao")
@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final PendingApprovalsService service;

    @GetMapping("/pending")
    @PreAuthorize("hasRole('SUPERVISOR')")
    @Operation(summary = "Fila de aprovacao (quatro olhos): cases, regras e PEPs pendentes; page/size valem para cada lista")
    public ResponseEntity<PendingApprovalsResponse> pending(Pageable pageable) {
        return ResponseEntity.ok(service.pending(pageable));
    }
}
