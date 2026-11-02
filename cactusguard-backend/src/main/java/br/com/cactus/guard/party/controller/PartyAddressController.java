package br.com.cactus.guard.party.controller;

import br.com.cactus.guard.party.entity.PartyAddress;
import br.com.cactus.guard.party.service.PartyAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/parties/{partyId}/addresses")
@RequiredArgsConstructor
public class PartyAddressController {

    private final PartyAddressService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('ANALYST', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<PartyAddress> add(@PathVariable UUID partyId, @RequestBody Map<String, Object> body) {
        PartyAddress address = service.add(
                partyId,
                (String) body.get("type"),
                (String) body.get("street"),
                (String) body.get("number"),
                (String) body.get("city"),
                (String) body.get("state"),
                (String) body.get("zipCode"),
                Boolean.TRUE.equals(body.get("primary"))
        );
        return ResponseEntity.ok(address);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST', 'SUPERVISOR', 'ADMIN')")
    public ResponseEntity<List<PartyAddress>> list(@PathVariable UUID partyId) {
        return ResponseEntity.ok(service.findByParty(partyId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
    public ResponseEntity<Void> remove(@PathVariable UUID id) {
        service.remove(id);
        return ResponseEntity.noContent().build();
    }
}
