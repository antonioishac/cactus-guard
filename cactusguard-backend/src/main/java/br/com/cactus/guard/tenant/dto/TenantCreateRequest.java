package br.com.cactus.guard.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Provisionamento de tenant: dados da instituicao + primeiro administrador (a senha e gerada pelo servidor). */
public record TenantCreateRequest(
        @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$",
                message = "code: minusculas, digitos e hifen (3 a 50 caracteres)") String code,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9./-]{14,18}$", message = "cnpj invalido") String cnpj,
        @NotBlank @Pattern(regexp = "BANK|PAYMENT_INSTITUTION|SCD|SEP|BROKER_CVM|INSURER_SUSEP|VASP|OTHER",
                message = "regulatorySegment invalido") String regulatorySegment,
        @NotBlank @Size(min = 3, max = 100) @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                message = "adminUsername: letras, digitos, ponto, hifen e underscore") String adminUsername,
        @NotBlank @Email @Size(max = 255) String adminEmail,
        @NotBlank @Size(max = 255) String adminName) {
}
