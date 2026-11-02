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
class PartyDeleteTest {

    @Autowired
    private PartyService partyService;

    @Test
    void deletePartyShouldRemove() {
        PartyRequest request = new PartyRequest();
        request.setType("PF");
        request.setDocument("25252525252");
        request.setName("To Delete");

        var created = partyService.create(request);
        assertNotNull(created.getId());

        partyService.delete(created.getId());

        assertThrows(IllegalArgumentException.class, () -> partyService.findById(created.getId()));
    }
}
