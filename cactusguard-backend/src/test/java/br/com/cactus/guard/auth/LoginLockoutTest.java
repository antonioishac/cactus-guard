package br.com.cactus.guard.auth;

import br.com.cactus.guard.auth.dto.LoginRequest;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.auth.service.AuthService;
import br.com.cactus.guard.auth.service.LoginBlockedException;
import br.com.cactus.guard.common.exception.GlobalExceptionHandler;
import br.com.cactus.guard.securityevent.dto.ClientInfo;
import br.com.cactus.guard.securityevent.entity.SecurityEvent;
import br.com.cactus.guard.securityevent.repository.SecurityEventRepository;
import br.com.cactus.guard.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/** Bloqueio progressivo de login e eventos de seguranca (chama o servico direto: o RateLimitFilter global e de 5/min). */
@SpringBootTest
@ActiveProfiles("test")
class LoginLockoutTest {

    private static final String PASSWORD = "Correct#Horse9";

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SecurityEventRepository events;

    private static ClientInfo newClient() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return new ClientInfo("10." + r.nextInt(1, 250) + "." + r.nextInt(1, 250) + "." + r.nextInt(1, 250), "JUnit/1.0");
    }

    private String newUser() {
        String username = TestUsers.unique("lock");
        userRepository.save(User.builder().username(username).email(username + "@test.local")
                .password(passwordEncoder.encode(PASSWORD)).name(username).active(true).build());
        return username;
    }

    private static LoginRequest req(String user, String pass) {
        LoginRequest r = new LoginRequest();
        r.setUsername(user);
        r.setPassword(pass);
        return r;
    }

    private void fail(String user, ClientInfo c, int times) {
        for (int i = 0; i < times; i++) {
            assertThrows(BadCredentialsException.class, () -> authService.login(req(user, "wrong-" + System.nanoTime()), c));
        }
    }

    @Test
    void blocksAfterFiveFailuresEvenWithCorrectPassword() {
        String user = newUser();
        ClientInfo c = newClient();
        fail(user, c, 5);

        LoginBlockedException ex = assertThrows(LoginBlockedException.class, () -> authService.login(req(user, PASSWORD), c));
        assertEquals(LoginBlockedException.MESSAGE, ex.getMessage());
        // outro IP, mesmo usuario: continua bloqueado (bloqueio por usuario)
        assertThrows(LoginBlockedException.class, () -> authService.login(req(user, PASSWORD), newClient()));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, new GlobalExceptionHandler().handleLoginBlocked(ex).getStatusCode());
    }

    @Test
    void fourFailuresDoNotBlock() {
        String user = newUser();
        ClientInfo c = newClient();
        fail(user, c, 4);
        assertNotNull(authService.login(req(user, PASSWORD), c).getAccessToken());
    }

    @Test
    void successfulLoginResetsTheFailureCounter() {
        String user = newUser();
        ClientInfo c = newClient();
        fail(user, c, 4);
        assertNotNull(authService.login(req(user, PASSWORD), c).getAccessToken());
        fail(user, c, 4); // 4 + 4 = 8 falhas no total, mas o contador foi zerado pelo sucesso
        assertNotNull(authService.login(req(user, PASSWORD), c).getAccessToken());
    }

    @Test
    void unknownUserGetsTheSameBlockedResponseAsExistingUser() {
        String existing = newUser();
        String ghost = TestUsers.unique("ghost");
        ClientInfo c1 = newClient();
        ClientInfo c2 = newClient();
        fail(existing, c1, 5);
        fail(ghost, c2, 5);

        LoginBlockedException a = assertThrows(LoginBlockedException.class, () -> authService.login(req(existing, "x"), newClient()));
        LoginBlockedException b = assertThrows(LoginBlockedException.class, () -> authService.login(req(ghost, "x"), newClient()));
        assertEquals(a.getMessage(), b.getMessage());
        // antes do bloqueio, existente e inexistente falham com a mesma excecao/mensagem
        BadCredentialsException e1 = assertThrows(BadCredentialsException.class, () -> authService.login(req(newUser(), "bad"), newClient()));
        BadCredentialsException e2 = assertThrows(BadCredentialsException.class, () -> authService.login(req(TestUsers.unique("ghost"), "bad"), newClient()));
        assertEquals(e1.getMessage(), e2.getMessage());
    }

    @Test
    void ipIsBlockedAfterTwentyFailuresAcrossUsernames() {
        ClientInfo c = newClient();
        for (int i = 0; i < 20; i++) {
            String ghost = TestUsers.unique("g");
            assertThrows(BadCredentialsException.class, () -> authService.login(req(ghost, "bad"), c));
        }
        String user = newUser();
        assertThrows(LoginBlockedException.class, () -> authService.login(req(user, PASSWORD), c));
        // outro IP nao e afetado
        assertNotNull(authService.login(req(user, PASSWORD), newClient()).getAccessToken());
    }

    @Test
    void eventsAreRecordedWithoutPasswordAndWithMaskedUsername() {
        String user = newUser();
        ClientInfo c = newClient();
        fail(user, c, 1);
        authService.login(req(user, PASSWORD), c);

        List<SecurityEvent> all = events.findAll().stream().filter(e -> c.ip().equals(e.getIpAddress())).toList();
        assertTrue(all.stream().anyMatch(e -> SecurityEvent.LOGIN_FAILURE.equals(e.getEventType())));
        assertTrue(all.stream().anyMatch(e -> SecurityEvent.LOGIN_SUCCESS.equals(e.getEventType())));
        for (SecurityEvent e : all) {
            assertNotEquals(user, e.getUsernameMasked(), "username nao pode ser gravado em claro");
            assertTrue(e.getUsernameMasked().contains("***"));
            assertEquals("JUnit/1.0", e.getUserAgent());
            String dump = e.toString() + e.getUsernameMasked() + e.getUsernameHash() + e.getUserAgent();
            assertFalse(dump.contains(PASSWORD));
            assertFalse(dump.contains("wrong-"));
        }
    }
}
