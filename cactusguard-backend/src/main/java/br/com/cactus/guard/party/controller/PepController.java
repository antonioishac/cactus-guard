package br.com.cactus.guard.party.controller;

import br.com.cactus.guard.party.dto.PepRequest;
import br.com.cactus.guard.party.dto.PepResponse;
import br.com.cactus.guard.party.service.PartyPepService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "PEP")
@RestController
@RequestMapping("/api/v1/parties/{partyId}/pep")
@RequiredArgsConstructor
public class PepController {

    private final PartyPepService pepService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Condicao de PEP da parte (categoria, vigencia, aprovacao, EDD)")
    public ResponseEntity<PepResponse> get(@PathVariable UUID partyId) {
        return ResponseEntity.ok(pepService.get(partyId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Marca/atualiza condicao de PEP (forca EDD e risco minimo HIGH)")
    public ResponseEntity<PepResponse> mark(@PathVariable UUID partyId, @Valid @RequestBody PepRequest request) {
        return ResponseEntity.ok(pepService.markPep(partyId, request));
    }

    @PostMapping("/approve")
    @PreAuthorize("hasRole('SUPERVISOR')")
    @Operation(summary = "Aprovacao da alta administracao para relacionamento com PEP")
    public ResponseEntity<PepResponse> approve(@PathVariable UUID partyId,
                                               @RequestParam(required = false) String comment) {
        return ResponseEntity.ok(pepService.approvePep(partyId, comment));
    }
}
