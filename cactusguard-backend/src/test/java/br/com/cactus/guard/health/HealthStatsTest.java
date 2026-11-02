package br.com.cactus.guard.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthStatsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void statsShouldContainAllCounters() throws Exception {
        mockMvc.perform(get("/api/v1/health/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parties").isNumber())
                .andExpect(jsonPath("$.alertsOpen").isNumber())
                .andExpect(jsonPath("$.alertsInAnalysis").isNumber())
                .andExpect(jsonPath("$.casesOpen").isNumber())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
