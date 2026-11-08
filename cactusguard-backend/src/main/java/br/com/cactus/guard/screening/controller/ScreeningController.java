package br.com.cactus.guard.screening.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import br.com.cactus.guard.screening.dto.ScreeningResultResponse;
import br.com.cactus.guard.screening.service.ScreeningService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/screening")
@RequiredArgsConstructor
public class ScreeningController {

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

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    public ResponseEntity<ScreeningResultResponse> review(
            @PathVariable UUID id,
            @RequestParam String status) {
        return ResponseEntity.ok(screeningService.review(id, status));
    }
}
