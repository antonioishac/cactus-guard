package br.com.cactus.guard.siscoaf.controller;

import br.com.cactus.guard.siscoaf.entity.SiscoafCommunication;
import br.com.cactus.guard.siscoaf.service.SarGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/siscoaf/sar")
@RequiredArgsConstructor
public class SarController {

    private final SarGeneratorService service;

    @PostMapping("/generate/{caseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public ResponseEntity<SiscoafCommunication> generate(@PathVariable UUID caseId) {
        return ResponseEntity.ok(service.generateSar(caseId));
    }
}
