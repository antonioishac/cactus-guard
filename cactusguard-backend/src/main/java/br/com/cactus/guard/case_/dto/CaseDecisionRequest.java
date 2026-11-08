package br.com.cactus.guard.case_.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CaseDecisionRequest {
    /** COMMUNICATE ou ARCHIVE (escalonamento tem endpoint proprio). */
    @NotBlank
    private String decision;
    /** Obrigatoria (minimo de 20 caracteres nao brancos). */
    private String justification;
}
