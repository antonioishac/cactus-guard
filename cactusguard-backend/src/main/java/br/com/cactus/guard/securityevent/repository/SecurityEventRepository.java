package br.com.cactus.guard.securityevent.repository;

import br.com.cactus.guard.securityevent.entity.SecurityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, UUID>, JpaSpecificationExecutor<SecurityEvent> {

    long countByUsernameHashAndEventTypeAndOccurredAtAfter(String usernameHash, String eventType, Instant since);

    boolean existsByUsernameHashAndEventTypeAndOccurredAtAfter(String usernameHash, String eventType, Instant since);

    Optional<SecurityEvent> findFirstByUsernameHashAndEventTypeOrderByOccurredAtDesc(String usernameHash, String eventType);

    long countByIpAddressAndEventTypeAndOccurredAtAfter(String ipAddress, String eventType, Instant since);

    boolean existsByIpAddressAndEventTypeAndOccurredAtAfter(String ipAddress, String eventType, Instant since);
}
