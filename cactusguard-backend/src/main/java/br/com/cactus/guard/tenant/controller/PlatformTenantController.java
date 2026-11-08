package br.com.cactus.guard.tenant.controller;

import br.com.cactus.guard.tenant.dto.TenantCreateRequest;
import br.com.cactus.guard.tenant.dto.TenantProvisionedResponse;
import br.com.cactus.guard.tenant.dto.TenantResponse;
import br.com.cactus.guard.tenant.dto.TenantStatusRequest;
import br.com.cactus.guard.tenant.service.PlatformTenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Gestao de tenants. Exclusivo de PLATFORM_ADMIN, que por sua vez NAO tem nenhuma das roles de negocio
 * (ADMIN/SUPERVISOR/ANALYST/AUDITOR): nao alcanca endpoint algum de dado de cliente.
 */
@Tag(name = "Plataforma - tenants")
@RestController
@RequestMapping("/api/v1/platform/tenants")
@RequiredArgsConstructor
public class PlatformTenantController {

    private final PlatformTenantService service;

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Provisiona tenant: cadastro + regras-modelo + primeiro ADMIN (senha temporaria devolvida uma unica vez)")
    public ResponseEntity<TenantProvisionedResponse> create(@Valid @RequestBody TenantCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.provision(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Lista tenants (paginado; CNPJ mascarado)")
    public ResponseEntity<Page<TenantResponse>> list(Pageable pageable) {
        return ResponseEntity.ok(service.list(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Detalhe de um tenant")
    public ResponseEntity<TenantResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Suspende o tenant (login e requisicoes passam a 403). Justificativa obrigatoria")
    public ResponseEntity<TenantResponse> suspend(@PathVariable UUID id, @Valid @RequestBody TenantStatusRequest body) {
        return ResponseEntity.ok(service.suspend(id, body.reason()));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Reativa o tenant. Justificativa obrigatoria")
    public ResponseEntity<TenantResponse> reactivate(@PathVariable UUID id, @Valid @RequestBody TenantStatusRequest body) {
        return ResponseEntity.ok(service.reactivate(id, body.reason()));
    }
}
