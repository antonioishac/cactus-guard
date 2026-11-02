package br.com.cactus.guard.siscoaf.controller;

import br.com.cactus.guard.siscoaf.dto.SiscoafResponse;
import br.com.cactus.guard.siscoaf.service.SiscoafService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/siscoaf")
@RequiredArgsConstructor
public class SiscoafController {

    private final SiscoafService siscoafService;

    @PostMapping("/draft")
    public ResponseEntity<SiscoafResponse> createDraft(
            @RequestParam UUID caseId,
            @RequestParam String type,
            @RequestBody(required = false) Map<String, Object> payload) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(siscoafService.createDraft(caseId, type, payload));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<SiscoafResponse> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(siscoafService.submit(id));
    }

    @GetMapping
    public ResponseEntity<Page<SiscoafResponse>> findByStatus(
            @RequestParam(defaultValue = "DRAFT") String status,
            Pageable pageable) {
        return ResponseEntity.ok(siscoafService.findByStatus(status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SiscoafResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(siscoafService.findById(id));
    }
}
