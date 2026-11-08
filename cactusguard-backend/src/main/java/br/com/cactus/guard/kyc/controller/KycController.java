package br.com.cactus.guard.kyc.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import br.com.cactus.guard.kyc.dto.KycProcessResponse;
import br.com.cactus.guard.kyc.service.KycService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
public class KycController {

    private final KycService kycService;

    @PostMapping("/onboarding/{partyId}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    public ResponseEntity<KycProcessResponse> startOnboarding(@PathVariable UUID partyId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(kycService.startOnboarding(partyId));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<KycProcessResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(kycService.approve(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<KycProcessResponse> reject(@PathVariable UUID id) {
        return ResponseEntity.ok(kycService.reject(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<Page<KycProcessResponse>> findByStatus(
            @RequestParam(defaultValue = "IN_PROGRESS") String status,
            Pageable pageable) {
        return ResponseEntity.ok(kycService.findByStatus(status, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<KycProcessResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(kycService.findById(id));
    }
}
