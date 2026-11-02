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
class PartyTradeNameTest {

    @Autowired
    private PartyService partyService;

    @Test
    void tradeNameShouldBeSaved() {
        PartyRequest request = new PartyRequest();
        request.setType("PJ");
        request.setDocument("65656565656");
        request.setName("Company Name");
        request.setTradeName("Trade Name Ltda");

        var created = partyService.create(request);
        assertEquals("Trade Name Ltda", created.getTradeName());
    }
}
