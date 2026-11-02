package br.com.cactus.guard.party.repository;

import br.com.cactus.guard.party.entity.PartyAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PartyAddressRepository extends JpaRepository<PartyAddress, UUID> {
    List<PartyAddress> findByPartyId(UUID partyId);
    List<PartyAddress> findByPartyIdAndType(UUID partyId, String type);
}
