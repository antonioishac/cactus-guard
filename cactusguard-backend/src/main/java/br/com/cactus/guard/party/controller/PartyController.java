package br.com.cactus.guard.party.controller;

import br.com.cactus.guard.party.dto.OnCreate;
import br.com.cactus.guard.party.dto.PartyFilter;
import br.com.cactus.guard.party.dto.PartyRequest;
import br.com.cactus.guard.party.dto.PartyResponse;
import br.com.cactus.guard.party.dto.RevealRequest;
import br.com.cactus.guard.party.dto.RevealResponse;
import br.com.cactus.guard.party.service.PartyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Partes")
@RestController
@RequestMapping("/api/v1/parties")
@RequiredArgsConstructor
public class PartyController {

    private final PartyService partyService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Cadastra parte (documento obrigatorio e imutavel depois)")
    public ResponseEntity<PartyResponse> create(@Validated(OnCreate.class) @RequestBody PartyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(partyService.create(request));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Busca full-text (legado; prefira GET /parties?q=)")
    public ResponseEntity<Page<PartyResponse>> search(
            @RequestParam String q,
            Pageable pageable) {
        return ResponseEntity.ok(partyService.search(q, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Ficha da parte (PII mascarada)")
    public ResponseEntity<PartyResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(partyService.findById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Lista partes com filtros: q (full-text), document (HMAC), type|personType, riskLevel, pep|isPep, sourceType")
    public ResponseEntity<Page<PartyResponse>> findAll(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String document,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String personType,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) Boolean pep,
            @RequestParam(required = false) Boolean isPep,
            @RequestParam(required = false) String sourceType,
            Pageable pageable) {
        PartyFilter filter = new PartyFilter(q, document, type != null ? type : personType, riskLevel,
                pep != null ? pep : isPep, sourceType);
        return ResponseEntity.ok(partyService.findAll(filter, pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Edita parte. Documento imutavel; PII com '*' (mascarada) e ignorada")
    public ResponseEntity<PartyResponse> update(@PathVariable UUID id, @Valid @RequestBody PartyRequest request) {
        return ResponseEntity.ok(partyService.update(id, request));
    }

    @PostMapping("/{id}/reveal")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','AUDITOR')")
    @Operation(summary = "Revela campo(s) de PII em claro; motivo obrigatorio, registrado em data_access_log (LGPD)")
    public ResponseEntity<RevealResponse> reveal(@PathVariable UUID id, @Valid @RequestBody RevealRequest request,
                                                 HttpServletRequest http) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(partyService.reveal(id, request, br.com.cactus.guard.config.ClientIpFilter.ipOf(http)));
    }

    @PostMapping("/{id}/risk/recalculate")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Recalcula o risco desta parte (RiskScoringService); registra historico e auditoria")
    public ResponseEntity<PartyResponse> recalculateRisk(@PathVariable UUID id) {
        return ResponseEntity.ok(partyService.recalculateRisk(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        partyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
