package br.com.cactus.guard.rule.controller;

import br.com.cactus.guard.rule.engine.RiskScoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskScoringService riskScoringService;

    @PostMapping("/recalculate")
    public ResponseEntity<Map<String, String>> recalculate() {
        riskScoringService.recalculateAllRiskScores();
        return ResponseEntity.ok(Map.of("status", "completed"));
    }
}
