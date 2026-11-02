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
class PartyFindByDocumentTest {

    @Autowired
    private PartyService partyService;

    @Test
    void findByDocumentShouldReturnParty() {
        PartyRequest request = new PartyRequest();
        request.setType("PF");
        request.setDocument("53535353535");
        request.setName("Find By Document");

        partyService.create(request);

        var found = partyService.findByDocument("53535353535");
        assertTrue(found.isPresent());
        assertEquals("Find By Document", found.get().getName());
    }
}
