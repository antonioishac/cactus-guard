package br.com.cactus.guard.health.controller;

import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.case_.repository.CaseRepository;
import br.com.cactus.guard.kyc.repository.KycProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final AlertRepository alertRepository;
    private final CaseRepository caseRepository;
    private final KycProcessRepository kycRepository;

    @GetMapping("/operational")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
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
}
