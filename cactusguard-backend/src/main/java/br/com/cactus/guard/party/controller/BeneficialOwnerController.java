package br.com.cactus.guard.party.controller;

import br.com.cactus.guard.party.entity.BeneficialOwner;
import br.com.cactus.guard.party.service.BeneficialOwnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/parties/{partyId}/beneficial-owners")
@RequiredArgsConstructor
public class BeneficialOwnerController {

    private final BeneficialOwnerService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    public ResponseEntity<BeneficialOwner> add(
            @PathVariable UUID partyId,
            @RequestParam String document,
            @RequestParam String name,
            @RequestParam(required = false) BigDecimal ownershipPct,
            @RequestParam(required = false) String controlType,
            @RequestParam(defaultValue = "false") boolean isPep) {
        return ResponseEntity.ok(service.add(partyId, document, name, ownershipPct, controlType, isPep));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<List<BeneficialOwner>> list(@PathVariable UUID partyId) {
        return ResponseEntity.ok(service.findByParty(partyId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        service.remove(id);
        return ResponseEntity.noContent().build();
    }
}
