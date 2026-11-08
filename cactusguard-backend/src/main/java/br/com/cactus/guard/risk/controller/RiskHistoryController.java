package br.com.cactus.guard.risk.controller;

import br.com.cactus.guard.risk.entity.RiskHistory;
import br.com.cactus.guard.risk.service.RiskHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/risk-history")
@RequiredArgsConstructor
public class RiskHistoryController {

    private final RiskHistoryService service;

    @GetMapping("/party/{partyId}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<Page<RiskHistory>> findByParty(@PathVariable UUID partyId, Pageable pageable) {
        return ResponseEntity.ok(service.findByParty(partyId, pageable));
    }
}
