package br.com.cactus.guard.party;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.party.service.PartyAddressService;
import br.com.cactus.guard.party.service.PartyRelationshipService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PartyAddressRelationshipTest {

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private PartyAddressService addressService;

    @Autowired
    private PartyRelationshipService relationshipService;

    @Test
    void shouldAddAddressAndRelationship() {
        Party party = Party.builder()
                .type("PF")
                .document("66600066600")
                .documentHash("hash666")
                .name("Address Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        Party related = Party.builder()
                .type("PJ")
                .document("66600066600001")
                .documentHash("hash666pj")
                .name("Related Company")
                .status("ACTIVE")
                .build();
        related = partyRepository.save(related);

        var address = addressService.add(party.getId(), "RESIDENCIAL", "Rua Teste", "123", "São Paulo", "SP", "01234-567", true);
        assertNotNull(address.getId());
        assertEquals("RESIDENCIAL", address.getType());

        var rel = relationshipService.add(party.getId(), related.getId(), "SOCIO", "Sócio da empresa");
        assertNotNull(rel.getId());
        assertEquals("SOCIO", rel.getRelationshipType());
    }
}
