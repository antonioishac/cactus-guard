package br.com.cactus.guard.party.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PartyRequest {
    @NotBlank
    @Pattern(regexp = "PF|PJ", message = "Type must be PF or PJ")
    private String type;

    @NotBlank
    private String document;

    @NotBlank
    private String name;

    private String tradeName;
    private String email;
    private String phone;
    private java.time.LocalDate birthDate;
    private String nationality;
    private String occupation;
}
