package br.com.cactus.guard.party;

import br.com.cactus.guard.party.dto.PartyRequest;
import br.com.cactus.guard.party.service.PartyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartySearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PartyService partyService;

    @Test
    @WithMockUser(roles = "ANALYST")
    void searchShouldReturnResults() throws Exception {
        PartyRequest request = new PartyRequest();
        request.setType("PF");
        request.setDocument("99988877766");
        request.setName("Fulltext Search Test");
        partyService.create(request);

        mockMvc.perform(get("/api/v1/parties/search").param("q", "Fulltext"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
