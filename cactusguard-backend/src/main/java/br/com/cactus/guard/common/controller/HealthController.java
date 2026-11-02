package br.com.cactus.guard.common.controller;

import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.case_.repository.CaseRepository;
import br.com.cactus.guard.party.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private final PartyRepository partyRepository;
    private final AlertRepository alertRepository;
    private final CaseRepository caseRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString(),
                "service", "cactusguard"
        ));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(Map.of(
                "parties", partyRepository.count(),
                "alertsOpen", alertRepository.countByStatus("OPEN"),
                "alertsInAnalysis", alertRepository.countByStatus("IN_ANALYSIS"),
                "casesOpen", caseRepository.count(),
                "timestamp", Instant.now().toString()
        ));
    }
}
