package br.com.cactus.guard.kyc.controller;

import br.com.cactus.guard.kyc.entity.KycDocument;
import br.com.cactus.guard.kyc.repository.KycDocumentRepository;
import br.com.cactus.guard.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kyc/documents")
@RequiredArgsConstructor
public class KycDocumentDownloadController {

    private final KycDocumentRepository repository;

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('ANALYST','SUPERVISOR','ADMIN','AUDITOR')")
    public ResponseEntity<ByteArrayResource> download(@PathVariable UUID id) {
        KycDocument doc = repository.findById(id)
                .orElseThrow(() -> new BusinessException("Documento não encontrado"));

        if (doc.getContent() == null) {
            throw new BusinessException("Conteúdo do documento não disponível");
        }

        ByteArrayResource resource = new ByteArrayResource(doc.getContent());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(doc.getContentType() != null ? doc.getContentType() : "application/octet-stream"))
                .contentLength(doc.getContent().length)
                .body(resource);
    }
}
