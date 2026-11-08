package br.com.cactus.guard.transaction.controller;

import br.com.cactus.guard.transaction.entity.TransactionProfile;
import br.com.cactus.guard.transaction.service.TransactionProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transaction-profiles")
@RequiredArgsConstructor
public class TransactionProfileController {

    private final TransactionProfileService service;

    @PostMapping("/calculate/{partyId}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    public ResponseEntity<TransactionProfile> calculate(@PathVariable UUID partyId) {
        return ResponseEntity.ok(service.calculate(partyId));
    }

    @GetMapping("/party/{partyId}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<TransactionProfile> findByParty(@PathVariable UUID partyId) {
        TransactionProfile profile = service.findByParty(partyId);
        return profile != null ? ResponseEntity.ok(profile) : ResponseEntity.notFound().build();
    }
}
