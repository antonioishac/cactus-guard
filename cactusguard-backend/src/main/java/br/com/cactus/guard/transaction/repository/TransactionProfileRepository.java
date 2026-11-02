package br.com.cactus.guard.transaction.repository;

import br.com.cactus.guard.transaction.entity.TransactionProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TransactionProfileRepository extends JpaRepository<TransactionProfile, UUID> {
    Optional<TransactionProfile> findByPartyId(UUID partyId);
}
