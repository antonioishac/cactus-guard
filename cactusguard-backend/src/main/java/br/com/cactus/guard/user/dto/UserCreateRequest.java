package br.com.cactus.guard.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.Set;

@Data
public class UserCreateRequest {
    @NotBlank
    private String username;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @jakarta.validation.constraints.Size(min = 10, max = 128, message = "deve ter entre 10 e 128 caracteres")
    private String password;

    @NotBlank
    private String name;

    private Set<String> roles;
}
