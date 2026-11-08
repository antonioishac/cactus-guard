package br.com.cactus.guard.auth.repository;

import br.com.cactus.guard.auth.entity.RefreshToken;
import br.com.cactus.guard.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    void deleteByUser(User user);
}
