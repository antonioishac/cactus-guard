package br.com.cactus.guard.transaction;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.transaction.dto.TransactionRequest;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PartyRepository partyRepository;

    private Party testParty;

    @BeforeEach
    void setup() {
        testParty = Party.builder()
                .type("PF")
                .document("55555555555")
                .name("Test Transaction Party")
                .status("ACTIVE")
                .build();
        testParty = partyRepository.save(testParty);
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void createTransactionShouldReturnCreated() throws Exception {
        TransactionRequest request = new TransactionRequest();
        request.setPartyId(testParty.getId());
        request.setType("PIX");
        request.setAmount(new BigDecimal("1500.00"));
        request.setTransactionDate(Instant.now());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(1500.00))
                .andExpect(jsonPath("$.type").value("PIX"))
                .andExpect(jsonPath("$.partyId").value(testParty.getId().toString()));
    }
}
