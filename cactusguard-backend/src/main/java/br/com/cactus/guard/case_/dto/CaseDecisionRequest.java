package br.com.cactus.guard.case_.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CaseDecisionRequest {
    @NotBlank
    private String decision; // APPROVED, REJECTED, ESCALATED
    private String justification;
}
