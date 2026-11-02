package br.com.cactus.guard.rule.controller;

import br.com.cactus.guard.rule.engine.RuleEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/rules/engine")
@RequiredArgsConstructor
public class RuleEngineController {

    private final RuleEngineService ruleEngineService;

    @PostMapping("/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateAll() {
        int generated = ruleEngineService.evaluateAllActiveRules();
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "alertsGenerated", generated
        ));
    }
}
