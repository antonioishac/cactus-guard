package br.com.cactus.guard.audit;

import br.com.cactus.guard.audit.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditService auditService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void findByEntityShouldReturnLogs() throws Exception {
        UUID entityId = UUID.randomUUID();
        auditService.log("TEST", "PARTY", entityId, null, Map.of("field", "value"));

        mockMvc.perform(get("/api/v1/audit/entity/PARTY/" + entityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
