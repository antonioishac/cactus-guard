package br.com.cactus.guard.kyc.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.cactus.guard.kyc.dto.KycDocumentResponse;
import br.com.cactus.guard.kyc.service.KycDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kyc/documents")
@RequiredArgsConstructor
public class KycDocumentController {

    private final KycDocumentService documentService;

    @PostMapping("/{processId}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR')")
    public ResponseEntity<KycDocumentResponse> add(
            @PathVariable UUID processId,
            @RequestParam String documentType,
            @RequestParam("file") MultipartFile file) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.addDocument(processId, documentType, file.getOriginalFilename(), 
                        file.getBytes(), file.getContentType()));
    }

    @GetMapping("/process/{processId}")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<List<KycDocumentResponse>> findByProcess(@PathVariable UUID processId) {
        return ResponseEntity.ok(documentService.findByProcess(processId));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public ResponseEntity<KycDocumentResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.approve(id));
    }
}
