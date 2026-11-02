package br.com.cactus.guard.case_.controller;

import br.com.cactus.guard.case_.dto.CaseDecisionRequest;
import br.com.cactus.guard.case_.dto.CaseResponse;
import br.com.cactus.guard.case_.service.CaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;

    @PostMapping("/from-alert/{alertId}")
    public ResponseEntity<CaseResponse> createFromAlert(@PathVariable UUID alertId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(caseService.createFromAlert(alertId));
    }

    @GetMapping
    public ResponseEntity<Page<CaseResponse>> findAll(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(caseService.findAll(status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CaseResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(caseService.findById(id));
    }

    @PostMapping("/{id}/decide")
    public ResponseEntity<CaseResponse> decide(
            @PathVariable UUID id,
            @Valid @RequestBody CaseDecisionRequest request) {
        return ResponseEntity.ok(caseService.decide(id, request));
    }
}
