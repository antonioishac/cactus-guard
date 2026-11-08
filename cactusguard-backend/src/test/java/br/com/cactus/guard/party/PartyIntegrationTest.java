package br.com.cactus.guard.party;

import br.com.cactus.guard.party.dto.PartyRequest;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "ANALYST")
    void createPartyShouldReturnCreated() throws Exception {
        PartyRequest request = new PartyRequest();
        request.setType("PF");
        request.setDocument("12345678901");
        request.setName("João Silva");
        request.setEmail("joao@email.com");

        mockMvc.perform(post("/api/v1/parties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                // documento sai mascarado na resposta (LGPD) - so os 4 ultimos digitos
                .andExpect(jsonPath("$.document").value("*******8901"))
                .andExpect(jsonPath("$.name").value("João Silva"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void createPartyWithDuplicateDocumentShouldFail() throws Exception {
        PartyRequest request = new PartyRequest();
        request.setType("PF");
        request.setDocument("99999999999");
        request.setName("Test User");

        mockMvc.perform(post("/api/v1/parties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/parties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                // BusinessException -> 422 (padrao do GlobalExceptionHandler)
                .andExpect(status().isUnprocessableEntity());
    }
}
