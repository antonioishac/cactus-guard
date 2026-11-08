package br.com.cactus.guard.alert;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
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

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private PartyRepository partyRepository;

    private Alert testAlert;

    @BeforeEach
    void setup() {
        Party party = Party.builder()
                .type("PF")
                .document("11111111111")
                .name("Test Party")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        testAlert = Alert.builder()
                .party(party)
                .type("SCREENING")
                .severity("HIGH")
                .status("OPEN")
                .title("Test Alert")
                .description("Integration test alert")
                .ruleCode("PEP_MATCH")
                .createdAt(Instant.now())
                .build();
        testAlert = alertRepository.save(testAlert);
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void listAlertsShouldReturnResults() throws Exception {
        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    // assume() vincula o alerta ao usuario logado, que precisa existir (admin e criado pelo DataInitializer)
    @WithMockUser(username = "admin", roles = "ANALYST")
    void assumeAlertShouldChangeStatus() throws Exception {
        mockMvc.perform(post("/api/v1/alerts/" + testAlert.getId() + "/assume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_ANALYSIS"));
    }
}
