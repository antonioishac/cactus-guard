package br.com.cactus.guard.event.controller;

import br.com.cactus.guard.event.dto.DeadLetterActionRequest;
import br.com.cactus.guard.event.dto.DeadLetterResponse;
import br.com.cactus.guard.event.service.DeadLetterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Operacao da DLQ de eventos (spec 047): listar, reprocessar e descartar (com justificativa, auditado). */
@Tag(name = "Eventos / DLQ")
@RestController
@RequestMapping("/api/v1/events/dead-letters")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cactus.events", name = "enabled", havingValue = "true")
public class DeadLetterController {

    private final DeadLetterService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    @Operation(summary = "Lista mensagens da DLQ do tenant (filtro opcional por status: PENDING, REPROCESSED, DISCARDED)")
    public ResponseEntity<Page<DeadLetterResponse>> list(@RequestParam(required = false) String status, Pageable pageable) {
        return ResponseEntity.ok(service.list(status, pageable));
    }

    @PostMapping("/{id}/reprocess")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    @Operation(summary = "Republica a mensagem da DLQ no topico de origem (observacao opcional, registrada e auditada)")
    public ResponseEntity<DeadLetterResponse> reprocess(@PathVariable UUID id,
                                                        @Valid @RequestBody(required = false) DeadLetterActionRequest request) {
        return ResponseEntity.ok(service.reprocess(id, request == null ? null : request.justification()));
    }

    @PostMapping("/{id}/discard")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    @Operation(summary = "Descarta a mensagem da DLQ: justificativa obrigatoria; a linha e mantida e a acao e auditada")
    public ResponseEntity<DeadLetterResponse> discard(@PathVariable UUID id,
                                                      @Valid @RequestBody DeadLetterActionRequest request) {
        return ResponseEntity.ok(service.discard(id, request.justification()));
    }
}
