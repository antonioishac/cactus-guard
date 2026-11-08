package br.com.cactus.guard.common;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthShouldReturnUp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("cactusguard"));
    }

    @Test
    @WithMockUser(roles = "ANALYST") // /stats exige autenticacao (so /api/v1/health e publico)
    void statsShouldReturnCounts() throws Exception {
        mockMvc.perform(get("/api/v1/health/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parties").exists())
                .andExpect(jsonPath("$.alertsOpen").exists());
    }
}
