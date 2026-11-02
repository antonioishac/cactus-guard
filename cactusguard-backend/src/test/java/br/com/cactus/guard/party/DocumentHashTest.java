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
class DocumentHashTest {

    @Autowired
    private PartyService partyService;

    @Test
    void createShouldSetDocumentHash() {
        PartyRequest request = new PartyRequest();
        request.setType("PF");
        request.setDocument("99988877766");
        request.setName("Hash Test");

        var response = partyService.create(request);

        assertNotNull(response.getId());
        assertEquals("LOW", response.getRiskLevel());
    }
}
