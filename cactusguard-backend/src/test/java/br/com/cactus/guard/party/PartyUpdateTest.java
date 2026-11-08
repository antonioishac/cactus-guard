package br.com.cactus.guard.party;

import br.com.cactus.guard.party.dto.PartyRequest;
import br.com.cactus.guard.party.service.PartyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PartyUpdateTest {

    @Autowired
    private PartyService partyService;

    @Autowired
    private br.com.cactus.guard.party.repository.PartyRepository partyRepository;

    @Test
    void updatePartyShouldPersistChanges() {
        PartyRequest create = new PartyRequest();
        create.setType("PF");
        create.setDocument("17171717171");
        create.setName("Original Name");
        create.setEmail("original@email.com");

        var created = partyService.create(create);

        PartyRequest update = new PartyRequest();
        update.setType("PF");
        update.setDocument("17171717171");
        update.setName("Updated Name");
        update.setEmail("updated@email.com");

        var updated = partyService.update(created.getId(), update);
        assertEquals("Updated Name", updated.getName());
        // resposta mascara o e-mail (LGPD); o valor real persiste criptografado e e decifrado pela entity
        assertEquals("u***d@email.com", updated.getEmail());
        assertEquals("updated@email.com", partyRepository.findById(created.getId()).orElseThrow().getEmail());
    }
}
