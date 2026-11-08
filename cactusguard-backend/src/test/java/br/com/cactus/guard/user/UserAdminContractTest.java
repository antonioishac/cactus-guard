package br.com.cactus.guard.user;

import br.com.cactus.guard.auth.entity.RefreshToken;
import br.com.cactus.guard.auth.repository.RefreshTokenRepository;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** PUT /users/{id}, reativacao e reset de senha (ADMIN). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAdminContractTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    private String createUser(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"email\":\"%s@t.local\",\"password\":\"%s\",\"name\":\"Nome\",\"roles\":[\"ANALYST\"]}"
                                .formatted(username, username, password)))
                .andExpect(status().isCreated()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("id").asString();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateChangesNameEmailAndRolesAndNeverReturnsPassword() throws Exception {
        String u = TestUsers.unique("upd");
        String id = createUser(u, "SenhaForte123");
        mvc.perform(put("/api/v1/users/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Novo Nome\",\"email\":\"" + u + "-novo@t.local\",\"roles\":[\"SUPERVISOR\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Novo Nome"))
                .andExpect(jsonPath("$.email").value(u + "-novo@t.local"))
                .andExpect(jsonPath("$.roles[0]").value("SUPERVISOR"))
                .andExpect(jsonPath("$.username").value(u))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRejectsUnknownRoleDuplicateEmailAndUnknownUser() throws Exception {
        String u1 = TestUsers.unique("dup1");
        String u2 = TestUsers.unique("dup2");
        String id1 = createUser(u1, "SenhaForte123");
        createUser(u2, "SenhaForte123");
        mvc.perform(put("/api/v1/users/" + id1).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"N\",\"email\":\"" + u1 + "@t.local\",\"roles\":[\"NAO_EXISTE\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/users/" + id1).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"N\",\"email\":\"" + u2 + "@t.local\",\"roles\":[\"ANALYST\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_IN_USE"));
        mvc.perform(put("/api/v1/users/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"N\",\"email\":\"x@t.local\",\"roles\":[\"ANALYST\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivateThenActivate() throws Exception {
        String id = createUser(TestUsers.unique("act"), "SenhaForte123");
        mvc.perform(post("/api/v1/users/" + id + "/deactivate")).andExpect(jsonPath("$.active").value(false));
        mvc.perform(post("/api/v1/users/" + id + "/activate")).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void resetPasswordGeneratesStrongTemporaryPasswordRevokesTokensAndNeverReturnsHash() throws Exception {
        String u = TestUsers.unique("rst");
        String id = createUser(u, "SenhaAntiga123");
        // gera um refresh token via login
        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u + "\",\"password\":\"SenhaAntiga123\"}"))
                .andExpect(status().isOk()).andReturn();
        String refresh = om.readTree(login.getResponse().getContentAsString()).get("refreshToken").asString();
        var user = userRepository.findByUsername(u).orElseThrow();
        assertFalse(refreshTokenRepository.findAll().stream().filter(t -> t.getUser().getId().equals(user.getId())).toList().isEmpty());

        MvcResult res = mvc.perform(post("/api/v1/users/" + id + "/reset-password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshTokensRevoked").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.hash").doesNotExist())
                .andReturn();
        JsonNode body = om.readTree(res.getResponse().getContentAsString());
        String temp = body.get("temporaryPassword").asString();
        assertTrue(temp.length() >= 16);
        assertTrue(temp.matches(".*[A-Z].*") && temp.matches(".*[a-z].*") && temp.matches(".*\\d.*") && temp.matches(".*[^A-Za-z0-9].*"));

        // senha antiga invalida, temporaria valida, refresh token antigo revogado
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u + "\",\"password\":\"SenhaAntiga123\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u + "\",\"password\":\"" + temp + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void resetPasswordWithDefinedPasswordDoesNotEchoIt() throws Exception {
        String u = TestUsers.unique("rst2");
        String id = createUser(u, "SenhaAntiga123");
        mvc.perform(post("/api/v1/users/" + id + "/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"NovaSenhaDefinida9\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + u + "\",\"password\":\"NovaSenhaDefinida9\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/users/" + id + "/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"curta\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void nonAdminIsForbidden() throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(put("/api/v1/users/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"N\",\"email\":\"x@t.local\",\"roles\":[\"ANALYST\"]}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/" + id + "/activate")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/" + id + "/reset-password")).andExpect(status().isForbidden());
    }
}
