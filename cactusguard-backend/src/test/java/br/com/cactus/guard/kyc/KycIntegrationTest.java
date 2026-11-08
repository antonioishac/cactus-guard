package br.com.cactus.guard.kyc;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KycIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PartyRepository partyRepository;

    private Party testParty;

    @BeforeEach
    void setup() {
        testParty = Party.builder()
                .type("PF")
                .document("88888888888")
                .name("KYC Test Party")
                .status("ACTIVE")
                .build();
        testParty = partyRepository.save(testParty);
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void startOnboardingAndApprove() throws Exception {
        String response = mockMvc.perform(post("/api/v1/kyc/onboarding/" + testParty.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.type").value("ONBOARDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(get("/api/v1/kyc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
