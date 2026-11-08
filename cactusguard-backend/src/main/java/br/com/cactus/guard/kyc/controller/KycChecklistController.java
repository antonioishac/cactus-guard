package br.com.cactus.guard.kyc.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import br.com.cactus.guard.kyc.dto.KycChecklistResponse;
import br.com.cactus.guard.kyc.service.KycChecklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kyc/checklist")
@RequiredArgsConstructor
public class KycChecklistController {

    private final KycChecklistService checklistService;

    @PostMapping("/{processId}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    public ResponseEntity<KycChecklistResponse> add(
            @PathVariable UUID processId,
            @RequestParam String itemCode,
            @RequestParam String description,
            @RequestParam(defaultValue = "true") boolean required) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(checklistService.addItem(processId, itemCode, description, required));
    }

    @GetMapping("/process/{processId}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<List<KycChecklistResponse>> findByProcess(@PathVariable UUID processId) {
        return ResponseEntity.ok(checklistService.findByProcess(processId));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    public ResponseEntity<KycChecklistResponse> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(checklistService.complete(id));
    }
}
