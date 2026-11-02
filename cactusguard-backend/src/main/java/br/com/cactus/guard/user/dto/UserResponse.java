package br.com.cactus.guard.user.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UserResponse {
    private UUID id;
    private String username;
    private String email;
    private String name;
    private boolean active;
    private List<String> roles;
    private Instant createdAt;
}
