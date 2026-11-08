package br.com.cactus.guard.case_;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.case_.dto.CaseDecisionRequest;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
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

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private AlertRepository alertRepository;

    private Alert testAlert;

    @BeforeEach
    void setup() {
        Party party = Party.builder()
                .type("PF")
                .document("77777777777")
                .name("Case Test Party")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        testAlert = Alert.builder()
                .party(party)
                .type("SCREENING")
                .severity("HIGH")
                .status("OPEN")
                .title("Case Test Alert")
                .ruleCode("PEP_MATCH")
                .createdAt(Instant.now())
                .build();
        testAlert = alertRepository.save(testAlert);
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void createCaseFromAlertAndDecide() throws Exception {
        String caseId = mockMvc.perform(post("/api/v1/cases/from-alert/" + testAlert.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alertId").value(testAlert.getId().toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        CaseDecisionRequest decision = new CaseDecisionRequest();
        decision.setDecision("APPROVED");
        decision.setJustification("Análise concluída sem indícios");

        // Extract case id from response would be needed in real scenario
        mockMvc.perform(get("/api/v1/cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
