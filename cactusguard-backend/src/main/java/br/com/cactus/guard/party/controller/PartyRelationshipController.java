package br.com.cactus.guard.party.controller;

import br.com.cactus.guard.party.entity.PartyRelationship;
import br.com.cactus.guard.party.service.PartyRelationshipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/parties/{partyId}/relationships")
@RequiredArgsConstructor
public class PartyRelationshipController {

    private final PartyRelationshipService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    public ResponseEntity<PartyRelationship> add(@PathVariable UUID partyId, @RequestBody Map<String, Object> body) {
        PartyRelationship rel = service.add(
                partyId,
                UUID.fromString((String) body.get("relatedPartyId")),
                (String) body.get("relationshipType"),
                (String) body.get("description")
        );
        return ResponseEntity.ok(rel);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<List<PartyRelationship>> list(@PathVariable UUID partyId) {
        return ResponseEntity.ok(service.findByParty(partyId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
