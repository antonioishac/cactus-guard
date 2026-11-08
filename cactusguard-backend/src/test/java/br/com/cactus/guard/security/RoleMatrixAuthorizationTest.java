package br.com.cactus.guard.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Matriz de roles: 403 para role indevida; para a correta, qualquer status diferente de 401/403. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleMatrixAuthorizationTest {

    @Autowired
    private MockMvc mvc;

    private static final String AUDIT = "/api/v1/audit/entity/PARTY/" + UUID.randomUUID();

    private void allowed(RequestBuilder rb) throws Exception {
        int s = mvc.perform(rb).andReturn().getResponse().getStatus();
        assertNotEquals(403, s);
        assertNotEquals(401, s);
    }

    private static RequestBuilder postJson(String url) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    // ---- audit: AUDITOR, ADMIN, SUPERVISOR ----
    @Test @WithMockUser(roles = "ANALYST")
    void auditDeniedForAnalyst() throws Exception { mvc.perform(get(AUDIT)).andExpect(status().isForbidden()); }

    @Test @WithMockUser(roles = "AUDITOR")
    void auditAllowedForAuditor() throws Exception { mvc.perform(get(AUDIT)).andExpect(status().isOk()); }

    @Test @WithMockUser(roles = "ANALYST")
    void dataAccessDeniedForAnalyst() throws Exception {
        mvc.perform(get("/api/v1/data-access/user/" + UUID.randomUUID())).andExpect(status().isForbidden());
    }

    // ---- migration / user / admin: ADMIN ----
    @Test @WithMockUser(roles = "SUPERVISOR")
    void migrationDeniedForSupervisor() throws Exception {
        mvc.perform(post("/api/v1/migration/batches/" + UUID.randomUUID() + "/process")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void migrationAllowedForAdmin() throws Exception { allowed(post("/api/v1/migration/batches/" + UUID.randomUUID() + "/process")); }

    @Test @WithMockUser(roles = "ANALYST")
    void usersDeniedForAnalyst() throws Exception { mvc.perform(get("/api/v1/users")).andExpect(status().isForbidden()); }

    @Test @WithMockUser(roles = "AUDITOR")
    void usersDeniedForAuditor() throws Exception { mvc.perform(get("/api/v1/users")).andExpect(status().isForbidden()); }

    @Test @WithMockUser(roles = "ADMIN")
    void usersAllowedForAdmin() throws Exception { mvc.perform(get("/api/v1/users")).andExpect(status().isOk()); }

    @Test @WithMockUser(roles = "SUPERVISOR")
    void rehashDeniedForSupervisor() throws Exception {
        mvc.perform(post("/api/v1/admin/rehash-documents")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void rehashAllowedForAdmin() throws Exception {
        mvc.perform(post("/api/v1/admin/rehash-documents")).andExpect(status().isOk());
    }

    // ---- watchlist: SUPERVISOR ----
    @Test @WithMockUser(roles = "ANALYST")
    void watchlistCreateDeniedForAnalyst() throws Exception {
        mvc.perform(post("/api/v1/watchlist/" + UUID.randomUUID() + "/deactivate")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles = "SUPERVISOR")
    void watchlistCreateAllowedForSupervisor() throws Exception { allowed(post("/api/v1/watchlist/" + UUID.randomUUID() + "/deactivate")); }

    // ---- party: leitura (inclui AUDITOR), escrita ANALYST/SUPERVISOR ----
    @Test @WithMockUser(roles = "AUDITOR")
    void partyReadAllowedForAuditor() throws Exception { mvc.perform(get("/api/v1/parties")).andExpect(status().isOk()); }

    @Test @WithMockUser(roles = "AUDITOR")
    void partyWriteDeniedForAuditor() throws Exception {
        mvc.perform(delete("/api/v1/parties/" + UUID.randomUUID())).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void partyWriteDeniedForAdmin() throws Exception {
        mvc.perform(delete("/api/v1/parties/" + UUID.randomUUID())).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles = "ANALYST")
    void partyWriteAllowedForAnalyst() throws Exception { allowed(postJson("/api/v1/parties")); }

    // ---- kyc approve: SUPERVISOR ----
    @Test @WithMockUser(roles = "ANALYST")
    void kycApproveDeniedForAnalyst() throws Exception {
        mvc.perform(post("/api/v1/kyc/" + UUID.randomUUID() + "/approve")).andExpect(status().isForbidden());
    }

    // ---- report ----
    @Test @WithMockUser(roles = "USER")
    void reportDeniedForUnknownRole() throws Exception { mvc.perform(get("/api/v1/reports")).andExpect(status().isForbidden()); }

    @Test @WithMockUser(roles = "AUDITOR")
    void reportListAllowedForAuditor() throws Exception { mvc.perform(get("/api/v1/reports")).andExpect(status().isOk()); }

    // ---- sem autenticacao / token invalido ----
    @Test
    void anonymousGets401() throws Exception { mvc.perform(get("/api/v1/parties")).andExpect(status().isUnauthorized()); }

    @Test
    void invalidBearerGets401() throws Exception {
        mvc.perform(get("/api/v1/parties").header("Authorization", "Bearer lixo.lixo.lixo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception { mvc.perform(get("/api/v1/health")).andExpect(status().isOk()); }
}
