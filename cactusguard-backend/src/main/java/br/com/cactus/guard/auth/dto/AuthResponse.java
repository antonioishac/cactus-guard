package br.com.cactus.guard.auth.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private UserInfo user;

    @Data
    @Builder
    public static class UserInfo {
        private UUID id;
        private String username;
        private String name;
        private List<String> roles;
    }
}
