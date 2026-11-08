package br.com.cactus.guard.screening.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.common.validation.Justifications;
import br.com.cactus.guard.screening.dto.ScreeningListResponse;
import br.com.cactus.guard.screening.dto.ScreeningResultResponse;
import br.com.cactus.guard.screening.dto.ScreeningReviewRequest;
import br.com.cactus.guard.screening.service.ScreeningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Tag(name = "Screening")
@RestController
@RequestMapping("/api/v1/screening")
@RequiredArgsConstructor
public class ScreeningController {

    private static final Set<String> REVIEW_STATUSES = Set.of("CONFIRMED", "FALSE_POSITIVE");

    private final ScreeningService screeningService;

    @PostMapping("/screen/{partyId}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    public ResponseEntity<ScreeningResultResponse> screen(
            @PathVariable UUID partyId,
            @RequestParam(defaultValue = "PEP,SANCTIONS") List<String> lists) {
        return ResponseEntity.ok(screeningService.screen(partyId, lists));
    }

    @PostMapping("/screen-async/{partyId}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    public ResponseEntity<String> screenAsync(
            @PathVariable UUID partyId,
            @RequestParam(defaultValue = "PEP,SANCTIONS") List<String> lists) {
        screeningService.screenAsync(partyId, lists);
        return ResponseEntity.accepted().body("Screening iniciado de forma assíncrona");
    }

    @GetMapping("/lists")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Catalogo real das listas de screening (codigo, tipo, fonte, exige indisponibilidade de ativos)")
    public ResponseEntity<List<ScreeningListResponse>> lists() {
        return ResponseEntity.ok(screeningService.catalog());
    }

    @GetMapping("/party/{partyId}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<List<ScreeningResultResponse>> findByParty(@PathVariable UUID partyId) {
        return ResponseEntity.ok(screeningService.findByParty(partyId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<Page<ScreeningResultResponse>> findByStatus(
            @RequestParam(defaultValue = "PENDING") String status,
            Pageable pageable) {
        return ResponseEntity.ok(screeningService.findByStatus(status, pageable));
    }

    @PostMapping(value = "/{id}/review", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Revisao do hit (JSON {status, justification}); justificativa obrigatoria (>= 20 caracteres)")
    public ResponseEntity<ScreeningResultResponse> reviewJson(@PathVariable UUID id,
                                                              @Valid @RequestBody ScreeningReviewRequest body) {
        return ResponseEntity.ok(doReview(id, body.status(), body.justification()));
    }

    /** Compatibilidade: status/justification como query string ou formulario (application/x-www-form-urlencoded). */
    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Revisao do hit (legado: parametros status e justification)")
    public ResponseEntity<ScreeningResultResponse> review(
            @PathVariable UUID id,
            @RequestParam String status,
            @RequestParam(required = false) String justification) {
        return ResponseEntity.ok(doReview(id, status, justification));
    }

    private ScreeningResultResponse doReview(UUID id, String status, String justification) {
        if (status == null || !REVIEW_STATUSES.contains(status)) {
            throw new IllegalArgumentException("status: deve ser CONFIRMED ou FALSE_POSITIVE");
        }
        String just = Justifications.require(justification, "revisar hit de screening");
        return screeningService.review(id, status, just);
    }
}
