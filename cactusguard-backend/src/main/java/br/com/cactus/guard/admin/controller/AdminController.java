package br.com.cactus.guard.admin.controller;

import br.com.cactus.guard.admin.service.DocumentRehashService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DocumentRehashService rehashService;

    @Operation(summary = "Recalcula document_hash (HMAC-SHA256 + pepper) de parties, UBOs e contrapartes")
    @PostMapping("/rehash-documents")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DocumentRehashService.Result> rehashDocuments() {
        return ResponseEntity.ok(rehashService.rehashAll());
    }
}
