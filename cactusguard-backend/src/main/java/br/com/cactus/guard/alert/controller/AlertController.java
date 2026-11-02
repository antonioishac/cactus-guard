package br.com.cactus.guard.alert.controller;

import br.com.cactus.guard.alert.dto.AlertResponse;
import br.com.cactus.guard.alert.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public ResponseEntity<Page<AlertResponse>> findAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            Pageable pageable) {
        return ResponseEntity.ok(alertService.findAll(status, severity, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(alertService.findById(id));
    }

    @PostMapping("/{id}/assume")
    public ResponseEntity<AlertResponse> assume(@PathVariable UUID id) {
        return ResponseEntity.ok(alertService.assume(id));
    }

    @PostMapping("/{id}/false-positive")
    public ResponseEntity<AlertResponse> falsePositive(
            @PathVariable UUID id,
            @RequestParam(required = false) String comment) {
        return ResponseEntity.ok(alertService.markFalsePositive(id, comment));
    }
}
