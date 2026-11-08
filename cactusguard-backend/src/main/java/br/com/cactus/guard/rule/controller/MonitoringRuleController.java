package br.com.cactus.guard.rule.controller;

import br.com.cactus.guard.rule.dto.MonitoringRuleRequest;
import br.com.cactus.guard.rule.dto.MonitoringRuleResponse;
import br.com.cactus.guard.rule.dto.RuleVersionResponse;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.rule.dto.RuleMetricsResponse;
import br.com.cactus.guard.rule.service.MonitoringRuleService;
import br.com.cactus.guard.rule.service.RuleQueryService;
import br.com.cactus.guard.common.util.DateRange;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@Tag(name = "Regras de monitoramento")
@RestController
@RequestMapping("/api/v1/rules")
@RequiredArgsConstructor
public class MonitoringRuleController {

    private final MonitoringRuleService ruleService;
    private final RuleQueryService queryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Lista regras (paginado). Padrao: so ativas; includeInactive=true ou status explicito (ex.: PENDING_APPROVAL) inclui as demais. Inclui metricas dos ultimos 30 dias")
    public ResponseEntity<Page<MonitoringRuleResponse>> list(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            Pageable pageable) {
        return ResponseEntity.ok(queryService.list(
                new RuleQueryService.Filter(status, category, severity, q, includeInactive), pageable));
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Detalhe da regra por codigo (governanca, versao vigente e metricas)")
    public ResponseEntity<MonitoringRuleResponse> get(@PathVariable String code) {
        return ResponseEntity.ok(queryService.get(code));
    }

    @GetMapping("/{code}/metrics")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Metricas da regra no periodo: alertas gerados e taxa de falso positivo (FP / encerrados)")
    public ResponseEntity<RuleMetricsResponse> metrics(@PathVariable String code,
                                                       @RequestParam(required = false) String from,
                                                       @RequestParam(required = false) String to) {
        DateRange range = DateRange.of(from, to);
        return ResponseEntity.ok(queryService.metrics(code, range.from(), range.toExclusive()));
    }


    @GetMapping("/category/{category}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Lista regras ativas por categoria")
    public ResponseEntity<List<MonitoringRuleResponse>> findByCategory(@PathVariable String category) {
        return ResponseEntity.ok(ruleService.findByCategory(category));
    }

    @GetMapping("/{code}/versions")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Historico de versoes da regra")
    public ResponseEntity<List<RuleVersionResponse>> versions(@PathVariable String code) {
        return ResponseEntity.ok(ruleService.versions(code));
    }

    @GetMapping("/{code}/versions/effective")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Versao da regra vigente em um instante (ISO-8601)")
    public ResponseEntity<RuleVersionResponse> effectiveAt(@PathVariable String code, @RequestParam Instant at) {
        return ruleService.versionEffectiveAt(code, at)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    @Operation(summary = "Cria regra (fica PENDING_APPROVAL ate aprovacao por outro usuario)")
    public ResponseEntity<MonitoringRuleResponse> create(@Valid @RequestBody MonitoringRuleRequest request) {
        MonitoringRule rule = fromRequest(request);
        rule.setCode(request.code());
        return ResponseEntity.status(HttpStatus.CREATED).body(ruleService.create(rule));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    @Operation(summary = "Altera regra (gera nova versao; justificativa obrigatoria; requer nova aprovacao)")
    public ResponseEntity<MonitoringRuleResponse> update(@PathVariable String code,
                                                         @Valid @RequestBody MonitoringRuleRequest request) {
        return ResponseEntity.ok(ruleService.update(code, fromRequest(request), request.reason()));
    }

    @PostMapping("/{code}/approve")
    @PreAuthorize("hasRole('SUPERVISOR')")
    @Operation(summary = "Aprova a regra (usuario diferente de quem criou/alterou)")
    public ResponseEntity<MonitoringRuleResponse> approve(@PathVariable String code,
                                                          @RequestParam(required = false) String comment) {
        return ResponseEntity.ok(ruleService.approve(code, comment));
    }

    @PatchMapping("/{code}/toggle")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    @Operation(summary = "active=false desativa; active=true solicita reativacao (exige aprovacao). reason obrigatoria")
    public ResponseEntity<MonitoringRuleResponse> toggle(
            @PathVariable String code,
            @RequestParam boolean active,
            @RequestParam String reason) {
        return ResponseEntity.ok(active
                ? ruleService.requestActivation(code, reason)
                : ruleService.deactivate(code, reason));
    }

    private static MonitoringRule fromRequest(MonitoringRuleRequest r) {
        MonitoringRule rule = new MonitoringRule();
        rule.setName(r.name());
        rule.setDescription(r.description());
        rule.setCategory(r.category());
        rule.setSeverity(r.severity());
        rule.setThresholdValue(r.thresholdValue());
        rule.setThresholdPeriodDays(r.thresholdPeriodDays());
        rule.setParameters(r.parameters());
        rule.setAppliesToRiskLevels(r.appliesToRiskLevels());
        rule.setCarta4001Reference(r.carta4001Reference());
        return rule;
    }
}
