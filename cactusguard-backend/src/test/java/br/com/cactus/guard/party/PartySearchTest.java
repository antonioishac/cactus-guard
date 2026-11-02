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
class PartySearchTest {

    @Autowired
    private PartyService partyService;

    @Test
    void searchByNameShouldFindParty() {
        PartyRequest request = new PartyRequest();
        request.setType("PF");
        request.setDocument("43434343434");
        request.setName("Unique Search Name");

        partyService.create(request);

        var page = partyService.search("Unique Search", org.springframework.data.domain.Pageable.unpaged());
        assertFalse(page.isEmpty());
        assertTrue(page.getContent().stream().anyMatch(p -> p.getName().contains("Unique Search")));
    }
}
