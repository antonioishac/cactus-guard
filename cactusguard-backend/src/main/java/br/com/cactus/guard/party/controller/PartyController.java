package br.com.cactus.guard.party.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import br.com.cactus.guard.party.dto.PartyRequest;
import br.com.cactus.guard.party.dto.PartyResponse;
import br.com.cactus.guard.party.service.PartyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/parties")
@RequiredArgsConstructor
public class PartyController {

    private final PartyService partyService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    public ResponseEntity<PartyResponse> create(@Valid @RequestBody PartyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(partyService.create(request));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<Page<PartyResponse>> search(
            @RequestParam String q,
            Pageable pageable) {
        return ResponseEntity.ok(partyService.search(q, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<PartyResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(partyService.findById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<Page<PartyResponse>> findAll(Pageable pageable) {
        return ResponseEntity.ok(partyService.findAll(pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    public ResponseEntity<PartyResponse> update(@PathVariable UUID id, @Valid @RequestBody PartyRequest request) {
        return ResponseEntity.ok(partyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        partyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
