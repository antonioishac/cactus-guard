package br.com.cactus.guard.siscoaf.controller;

import br.com.cactus.guard.siscoaf.dto.SiscoafResponse;
import br.com.cactus.guard.siscoaf.service.SarGeneratorService;
import br.com.cactus.guard.siscoaf.service.SiscoafService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "SISCOAF")
@RestController
@RequestMapping("/api/v1/siscoaf/sar")
@RequiredArgsConstructor
public class SarController {

    private final SarGeneratorService service;

    @PostMapping("/generate/{caseId}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Gera rascunho de COS (legado SAR) para case com decisao COMMUNICATE aprovada")
    public ResponseEntity<SiscoafResponse> generate(@PathVariable UUID caseId) {
        return ResponseEntity.ok(SiscoafService.toResponse(service.generateSar(caseId)));
    }
}
