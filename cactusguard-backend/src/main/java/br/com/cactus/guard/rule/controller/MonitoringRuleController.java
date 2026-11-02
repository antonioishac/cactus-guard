package br.com.cactus.guard.rule.controller;

import br.com.cactus.guard.rule.dto.MonitoringRuleResponse;
import br.com.cactus.guard.rule.service.MonitoringRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rules")
@RequiredArgsConstructor
public class MonitoringRuleController {

    private final MonitoringRuleService ruleService;

    @GetMapping
    public ResponseEntity<List<MonitoringRuleResponse>> findAllActive() {
        return ResponseEntity.ok(ruleService.findAllActive());
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<MonitoringRuleResponse>> findByCategory(@PathVariable String category) {
        return ResponseEntity.ok(ruleService.findByCategory(category));
    }

    @PatchMapping("/{code}/toggle")
    public ResponseEntity<MonitoringRuleResponse> toggle(
            @PathVariable String code,
            @RequestParam boolean active) {
        return ResponseEntity.ok(ruleService.toggleActive(code, active));
    }
}
