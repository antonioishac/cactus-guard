package br.com.cactus.guard.case_.controller;

import br.com.cactus.guard.case_.dto.CaseApprovalRequest;
import br.com.cactus.guard.case_.dto.CaseCreateRequest;
import br.com.cactus.guard.case_.dto.CaseDecisionRequest;
import br.com.cactus.guard.case_.dto.CaseDossierResponse;
import br.com.cactus.guard.case_.dto.CaseEscalationRequest;
import br.com.cactus.guard.case_.dto.CaseEvidenceResponse;
import br.com.cactus.guard.case_.dto.CaseNoteRequest;
import br.com.cactus.guard.case_.dto.CaseNoteResponse;
import br.com.cactus.guard.case_.dto.CaseRejectApprovalRequest;
import br.com.cactus.guard.case_.dto.CaseResponse;
import br.com.cactus.guard.case_.repository.CaseSpecifications;
import br.com.cactus.guard.case_.service.CaseDossierService;
import br.com.cactus.guard.case_.service.CaseEvidenceService;
import br.com.cactus.guard.case_.service.CaseNoteService;
import br.com.cactus.guard.case_.service.CaseService;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.common.util.DateRange;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Tag(name = "Cases")
@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;
    private final CaseNoteService noteService;
    private final CaseEvidenceService evidenceService;
    private final CaseDossierService dossierService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Seleciona o alerta e abre o case (justificativa obrigatoria; inicia os 45 dias de analise)")
    public ResponseEntity<CaseResponse> create(@Valid @RequestBody CaseCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(caseService.createFromAlert(request.alertId(), request.justification()));
    }

    @PostMapping("/from-alert/{alertId}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Alias legado de POST /cases (justificativa opcional no corpo)")
    public ResponseEntity<CaseResponse> createFromAlert(@PathVariable UUID alertId,
                                                        @RequestBody(required = false) CaseFromAlertBody body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(caseService.createFromAlert(alertId, body != null ? body.justification() : null));
    }

    /** Corpo opcional do alias legado. */
    public record CaseFromAlertBody(String justification) {
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Lista cases (paginado). status aceita varios valores; slaStatus calculado pelos prazos regulatorios")
    public ResponseEntity<Page<CaseResponse>> findAll(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(required = false) String slaStatus,
            @RequestParam(required = false) String riskCategory,
            @RequestParam(required = false) Boolean isEdd,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) UUID partyId,
            Pageable pageable) {
        DateRange range = DateRange.of(from, to);
        var filter = new CaseSpecifications.Filter(status, assignedTo, slaStatus, riskCategory, isEdd,
                range.from(), range.toExclusive(), partyId);
        return ResponseEntity.ok(caseService.findAll(filter, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Detalha um case")
    public ResponseEntity<CaseResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(caseService.findById(id));
    }

    @GetMapping("/{id}/dossier")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Dossie consolidado (parte, risco, PEP, UBO, transacoes, screening, evidencias, notas, auditoria); PII mascarada")
    public ResponseEntity<CaseDossierResponse> dossier(@PathVariable UUID id) {
        return ResponseEntity.ok(dossierService.dossier(id));
    }

    @PostMapping("/{id}/decide")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Decisao humana: COMMUNICATE ou ARCHIVE (justificativa obrigatoria)")
    public ResponseEntity<CaseResponse> decide(
            @PathVariable UUID id,
            @Valid @RequestBody CaseDecisionRequest request) {
        return ResponseEntity.ok(caseService.decide(id, request));
    }

    @PostMapping("/{id}/escalate")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Escalona o case (nao e decisao final; justificativa obrigatoria)")
    public ResponseEntity<CaseResponse> escalate(
            @PathVariable UUID id,
            @Valid @RequestBody CaseEscalationRequest request) {
        return ResponseEntity.ok(caseService.escalate(id, request));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPERVISOR')")
    @Operation(summary = "Aprovacao quatro olhos (SUPERVISOR diferente de quem decidiu)")
    public ResponseEntity<CaseResponse> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) CaseApprovalRequest request) {
        return ResponseEntity.ok(caseService.approve(id, request));
    }

    @PostMapping("/{id}/reject-approval")
    @PreAuthorize("hasRole('SUPERVISOR')")
    @Operation(summary = "Rejeita a aprovacao (SUPERVISOR diferente do decisor; justificativa obrigatoria); devolve o case para analise")
    public ResponseEntity<CaseResponse> rejectApproval(
            @PathVariable UUID id,
            @Valid @RequestBody CaseRejectApprovalRequest request) {
        return ResponseEntity.ok(caseService.rejectApproval(id, request.justification()));
    }

    // ---------------------------------------------------------------- notas

    @GetMapping("/{id}/notes")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Notas de analise do case")
    public ResponseEntity<List<CaseNoteResponse>> notes(@PathVariable UUID id) {
        return ResponseEntity.ok(noteService.list(id));
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Adiciona nota de analise (imutavel)")
    public ResponseEntity<CaseNoteResponse> addNote(@PathVariable UUID id, @Valid @RequestBody CaseNoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(noteService.add(id, request.text()));
    }

    // ---------------------------------------------------------------- evidencias

    @GetMapping("/{id}/evidences")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Evidencias do case")
    public ResponseEntity<List<CaseEvidenceResponse>> evidences(@PathVariable UUID id) {
        return ResponseEntity.ok(evidenceService.list(id));
    }

    @PostMapping(value = "/{id}/evidences", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    @Operation(summary = "Anexa evidencia (multipart: file + description opcional). PDF/JPG/PNG, validados por assinatura; sha256 registrado")
    public ResponseEntity<CaseEvidenceResponse> upload(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(evidenceService.upload(
                    id, file.getOriginalFilename(), file.getContentType(), file.getBytes(), description));
        } catch (IOException e) {
            throw new BusinessException("Nao foi possivel ler o arquivo enviado");
        }
    }

    @GetMapping({"/{id}/evidences/{evidenceId}", "/{id}/evidences/{evidenceId}/download"})
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    @Operation(summary = "Download da evidencia (revalida sha256; acesso registrado)")
    public ResponseEntity<ByteArrayResource> download(@PathVariable UUID id, @PathVariable UUID evidenceId) {
        CaseEvidenceService.EvidenceFile f = evidenceService.download(id, evidenceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(f.contentType()))
                .contentLength(f.content().length)
                .body(new ByteArrayResource(f.content()));
    }
}
