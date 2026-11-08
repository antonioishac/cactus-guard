package br.com.cactus.guard.transaction.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import br.com.cactus.guard.transaction.dto.TransactionRequest;
import br.com.cactus.guard.transaction.dto.TransactionResponse;
import br.com.cactus.guard.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.create(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<TransactionResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(transactionService.findById(id));
    }

    @GetMapping("/party/{partyId}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<Page<TransactionResponse>> findByParty(
            @PathVariable UUID partyId,
            Pageable pageable) {
        return ResponseEntity.ok(transactionService.findByParty(partyId, pageable));
    }
}
