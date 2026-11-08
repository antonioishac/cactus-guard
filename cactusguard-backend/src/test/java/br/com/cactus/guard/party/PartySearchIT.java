package br.com.cactus.guard.party;

import br.com.cactus.guard.party.dto.PartyRequest;
import br.com.cactus.guard.party.service.PartyService;
import br.com.cactus.guard.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Busca full-text de partes ({@code tsvector} + trigger da V14 + {@code plainto_tsquery}).
 * <p>
 * Antes eram PartySearchTest e PartySearchControllerTest no perfil H2, onde a query nativa
 * PostgreSQL nunca poderia rodar; agora executam contra PostgreSQL real.
 */
@AutoConfigureMockMvc
class PartySearchIT extends AbstractPostgresIT {

    @Autowired
    private PartyService partyService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void searchByNameShouldFindParty() {
        PartyRequest request = new PartyRequest();
        request.setType("PF");
        request.setDocument("43434343434");
        request.setName("Unique Search Name");
        partyService.create(request);

        var page = partyService.search("Unique Search", Pageable.unpaged());

        assertFalse(page.isEmpty());
        assertTrue(page.getContent().stream().anyMatch(p -> p.getName().contains("Unique Search")));
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void searchEndpointShouldReturnResults() throws Exception {
        PartyRequest request = new PartyRequest();
        request.setType("PF");
        request.setDocument("99988877700");
        request.setName("Fulltext Search Test");
        partyService.create(request);

        mockMvc.perform(get("/api/v1/parties/search").param("q", "Fulltext"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
