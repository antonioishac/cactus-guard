package br.com.cactus.guard.health.controller;

import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.case_.repository.CaseRepository;
import br.com.cactus.guard.health.service.DashboardService;
import br.com.cactus.guard.kyc.repository.KycProcessRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Dashboard")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final AlertRepository alertRepository;
    private final CaseRepository caseRepository;
    private final KycProcessRepository kycRepository;
    private final DashboardService dashboardService;

    @GetMapping("/operational")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Indicadores operacionais")
    public ResponseEntity<Map<String, Object>> operational() {
        long openAlerts = alertRepository.countByStatus("OPEN");
        long openCases = caseRepository.countByStatus("OPEN");
        long pendingKyc = kycRepository.countByStatus("IN_PROGRESS");
        long criticalAlerts = alertRepository.countBySeverityAndStatus("CRITICAL", "OPEN");

        return ResponseEntity.ok(Map.of(
                "openAlerts", openAlerts,
                "openCases", openCases,
                "pendingKyc", pendingKyc,
                "criticalAlerts", criticalAlerts,
                "alertCaseRatio", openAlerts > 0 ? (double) openCases / openAlerts : 0
        ));
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Alertas por status, screenings pendentes, PEPs ativos, cases em aberto e pendentes de aprovacao")
    public ResponseEntity<Map<String, Object>> metrics() {
        return ResponseEntity.ok(dashboardService.metrics());
    }

    @GetMapping("/alerts-chart")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Serie temporal de alertas criados por dia (days: 1 a 90, padrao 7), com zeros nos dias vazios")
    public ResponseEntity<List<DashboardService.ChartPoint>> alertsChart(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(dashboardService.alertsChart(days));
    }

    @GetMapping("/severity-distribution")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Alertas em tratamento por severidade")
    public ResponseEntity<List<DashboardService.SeverityCount>> severityDistribution() {
        return ResponseEntity.ok(dashboardService.severityDistribution());
    }

    @GetMapping("/case-sla")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Prazos regulatorios: selecao, analise e comunicacao (no prazo / vencendo / vencidos) e cases pendentes de aprovacao")
    public ResponseEntity<DashboardService.CaseSla> caseSla() {
        return ResponseEntity.ok(dashboardService.caseSla());
    }
}
