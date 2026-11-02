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
class PartyFindAllTest {

    @Autowired
    private PartyService partyService;

    @Test
    void findAllShouldReturnPage() {
        PartyRequest request = new PartyRequest();
        request.setType("PJ");
        request.setDocument("31313131313");
        request.setName("Find All Test");
        request.setTradeName("Trade Name");

        partyService.create(request);

        var page = partyService.findAll(org.springframework.data.domain.Pageable.unpaged());
        assertFalse(page.isEmpty());
    }
}
