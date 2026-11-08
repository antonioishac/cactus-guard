package br.com.cactus.guard.device;

import br.com.cactus.guard.audit.repository.AuditLogRepository;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.device.repository.UserDeviceRepository;
import br.com.cactus.guard.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeviceControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserDeviceRepository deviceRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private static String body(String token) {
        return "{\"token\":\"%s\",\"platform\":\"ANDROID\",\"appVersion\":\"1.0.0\"}".formatted(token);
    }

    private static RequestPostProcessor as(String username, String role) {
        return user(username).roles(role);
    }

    @Test
    void registerUpsertTransferAndRevoke() throws Exception {
        String u1 = TestUsers.unique("dev1");
        String u2 = TestUsers.unique("dev2");
        TestUsers.ensure(userRepository, u1);
        TestUsers.ensure(userRepository, u2);
        String token = "fcm-token-" + TestUsers.unique("t") + "-abcdefghijklmnop";

        mvc.perform(post("/api/v1/devices").with(as(u1, "ANALYST"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.platform").value("ANDROID"));
        mvc.perform(post("/api/v1/devices").with(as(u1, "ANALYST"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(token)))
                .andExpect(status().isCreated());
        assertEquals(userRepository.findByUsername(u1).orElseThrow().getId(), deviceRepository.findByToken(token).orElseThrow().getUserId());

        // outro usuario nao consegue revogar -> 404
        mvc.perform(delete("/api/v1/devices/" + token).with(as(u2, "SUPERVISOR")))
                .andExpect(status().isNotFound());

        // transferencia para u2 (auditada)
        long auditBefore = auditLogRepository.count();
        mvc.perform(post("/api/v1/devices").with(as(u2, "SUPERVISOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(token)))
                .andExpect(status().isCreated());
        assertEquals(userRepository.findByUsername(u2).orElseThrow().getId(), deviceRepository.findByToken(token).orElseThrow().getUserId());
        assertTrue(auditLogRepository.count() > auditBefore);

        mvc.perform(delete("/api/v1/devices/" + token).with(as(u2, "SUPERVISOR")))
                .andExpect(status().isNoContent());
        assertNotNull(deviceRepository.findByToken(token).orElseThrow().getRevokedAt());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void invalidPlatformIs400() throws Exception {
        mvc.perform(post("/api/v1/devices").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"x\",\"platform\":\"PALM\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mvc.perform(post("/api/v1/devices").contentType(MediaType.APPLICATION_JSON).content(body("x")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void preferencesDefaultAndUpdate() throws Exception {
        String u = TestUsers.unique("pref");
        TestUsers.ensure(userRepository, u);
        mvc.perform(get("/api/v1/devices/preferences").with(as(u, "AUDITOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criticalAlerts").value(true))
                .andExpect(jsonPath("$.deadlines").value(true));
        mvc.perform(put("/api/v1/devices/preferences").with(as(u, "AUDITOR")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"criticalAlerts\":false,\"deadlines\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/devices/preferences").with(as(u, "AUDITOR")))
                .andExpect(jsonPath("$.criticalAlerts").value(false))
                .andExpect(jsonPath("$.deadlines").value(true));
        mvc.perform(put("/api/v1/devices/preferences").with(as(u, "AUDITOR")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deadlines\":true}"))
                .andExpect(status().isBadRequest());
    }
}
