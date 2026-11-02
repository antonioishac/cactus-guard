package br.com.cactus.guard.risk.controller;

import br.com.cactus.guard.rule.engine.RiskScoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
public class RiskRecalculationController {

    private final RiskScoringService riskScoringService;

    @PostMapping("/recalculate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> recalculate() {
        riskScoringService.recalculateAllRiskScores();
        return ResponseEntity.ok(Map.of("status", "COMPLETED", "message", "Risco recalculado para todas as partes"));
    }
}
