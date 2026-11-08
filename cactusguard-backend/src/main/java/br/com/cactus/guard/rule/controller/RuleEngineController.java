package br.com.cactus.guard.rule.controller;

import br.com.cactus.guard.rule.engine.RuleEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Regras de monitoramento")
@RestController
@RequestMapping("/api/v1/rules/engine")
@RequiredArgsConstructor
public class RuleEngineController {

    private final RuleEngineService ruleEngineService;

    @PostMapping("/evaluate")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    @Operation(summary = "Executa o motor de regras sob demanda")
    public ResponseEntity<Map<String, Object>> evaluateAll() {
        int generated = ruleEngineService.evaluateAllActiveRules();
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "alertsGenerated", generated
        ));
    }
}
