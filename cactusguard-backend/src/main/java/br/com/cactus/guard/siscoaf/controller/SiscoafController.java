package br.com.cactus.guard.siscoaf.controller;

import br.com.cactus.guard.siscoaf.dto.SiscoafReasonRequest;
import br.com.cactus.guard.siscoaf.dto.SiscoafResponse;
import br.com.cactus.guard.siscoaf.service.NegativeDeclarationService;
import br.com.cactus.guard.siscoaf.service.SiscoafService;
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

import java.util.Map;
import java.util.UUID;

@Tag(name = "SISCOAF")
@RestController
@RequestMapping("/api/v1/siscoaf")
@RequiredArgsConstructor
public class SiscoafController {

    private final SiscoafService siscoafService;
    private final NegativeDeclarationService negativeDeclarationService;

    @PostMapping("/draft")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Cria rascunho de comunicacao para um case")
    public ResponseEntity<SiscoafResponse> createDraft(
            @RequestParam UUID caseId,
            @RequestParam String type,
            @RequestBody(required = false) Map<String, Object> payload) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(siscoafService.createDraft(caseId, type, payload));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('SUPERVISOR')")
    @Operation(summary = "Envia a comunicacao ao COAF (acao explicita de SUPERVISOR)")
    public ResponseEntity<SiscoafResponse> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(siscoafService.submit(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('SUPERVISOR')")
    @Operation(summary = "Cancela comunicacao nao enviada (justificativa obrigatoria)")
    public ResponseEntity<SiscoafResponse> cancel(@PathVariable UUID id, @Valid @RequestBody SiscoafReasonRequest request) {
        return ResponseEntity.ok(siscoafService.cancel(id, request.reason()));
    }

    @PostMapping("/{id}/rectify")
    @PreAuthorize("hasRole('SUPERVISOR')")
    @Operation(summary = "Retifica comunicacao enviada (gera novo rascunho vinculado)")
    public ResponseEntity<SiscoafResponse> rectify(@PathVariable UUID id, @Valid @RequestBody SiscoafReasonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(siscoafService.rectify(id, request.reason(), request.payload()));
    }

    @PostMapping("/negative-declaration/{year}")
    @PreAuthorize("hasRole('SUPERVISOR')")
    @Operation(summary = "Gera a declaracao negativa do ano (somente se nao houve COS/COE enviadas)")
    public ResponseEntity<SiscoafResponse> negativeDeclaration(@PathVariable int year) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SiscoafService.toResponse(negativeDeclarationService.generate(year)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Lista comunicacoes por status (paginado)")
    public ResponseEntity<Page<SiscoafResponse>> findByStatus(
            @RequestParam(defaultValue = "DRAFT") String status,
            Pageable pageable) {
        return ResponseEntity.ok(siscoafService.findByStatus(status, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Detalha uma comunicacao")
    public ResponseEntity<SiscoafResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(siscoafService.findById(id));
    }
}
