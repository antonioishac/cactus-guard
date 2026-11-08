package br.com.cactus.guard.tenant.dto;

import br.com.cactus.guard.common.util.DataMasker;
import br.com.cactus.guard.tenant.entity.Tenant;

import java.time.Instant;
import java.util.UUID;

/** Visao de plataforma de um tenant. O CNPJ sai sempre mascarado. */
public record TenantResponse(
        UUID id,
        String code,
        String name,
        String cnpjMasked,
        String regulatorySegment,
        String status,
        Instant createdAt) {

    public static TenantResponse from(Tenant t) {
        return new TenantResponse(t.getId(), t.getCode(), t.getName(),
                t.getCnpj() == null ? null : DataMasker.maskDocument(t.getCnpj()),
                t.getRegulatorySegment(), t.getStatus(), t.getCreatedAt());
    }
}
