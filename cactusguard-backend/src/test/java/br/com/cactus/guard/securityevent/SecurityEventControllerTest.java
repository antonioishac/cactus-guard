package br.com.cactus.guard.securityevent;

import br.com.cactus.guard.securityevent.entity.SecurityEvent;
import br.com.cactus.guard.securityevent.repository.SecurityEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityEventControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private SecurityEventRepository repository;

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void platformAdminListsAndFiltersEvents() throws Exception {
        String ip = "203.0.113." + (int) (Math.random() * 200 + 1);
        repository.save(SecurityEvent.builder().eventType(SecurityEvent.LOGIN_FAILURE).result(SecurityEvent.RESULT_FAILURE)
                .usernameMasked("a***z").usernameHash("h" + UUID.randomUUID()).ipAddress(ip).userAgent("JUnit").build());

        mvc.perform(get("/api/v1/platform/security-events").param("ip", ip).param("eventType", "LOGIN_FAILURE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].usernameMasked").value("a***z"))
                .andExpect(jsonPath("$.content[0].usernameHash").doesNotExist());
    }

    @Test
    @WithMockUser(roles = {"ADMIN", "SUPERVISOR", "ANALYST", "AUDITOR"})
    void businessRolesCannotListEvents() throws Exception {
        mvc.perform(get("/api/v1/platform/security-events")).andExpect(status().isForbidden());
    }
}
