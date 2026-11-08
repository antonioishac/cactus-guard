package br.com.cactus.guard.device;

import br.com.cactus.guard.auth.entity.Role;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.RoleRepository;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.common.event.CasePendingApprovalEvent;
import br.com.cactus.guard.device.dto.DeviceRegisterRequest;
import br.com.cactus.guard.device.dto.PushPreferencesDto;
import br.com.cactus.guard.device.entity.DevicePlatform;
import br.com.cactus.guard.device.push.PushEventListener;
import br.com.cactus.guard.device.repository.UserDeviceRepository;
import br.com.cactus.guard.device.service.DeviceService;
import br.com.cactus.guard.integration.push.PushMessage;
import br.com.cactus.guard.integration.push.PushResult;
import br.com.cactus.guard.integration.push.PushSenderPort;
import br.com.cactus.guard.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PushDispatchTest {

    record Sent(String token, PushMessage msg) {
    }

    static final List<Sent> SENT = new CopyOnWriteArrayList<>();
    static final Set<String> INVALID = new CopyOnWriteArraySet<>();

    @TestConfiguration
    static class Cfg {
        @Bean
        @Primary
        PushSenderPort recordingSender() {
            return (token, msg) -> {
                SENT.add(new Sent(token, msg));
                return INVALID.contains(token) ? PushResult.INVALID_TOKEN : PushResult.SENT;
            };
        }
    }

    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired DeviceService deviceService;
    @Autowired UserDeviceRepository devices;
    @Autowired PushEventListener listener;

    private User supervisor(String name) {
        User u = TestUsers.ensure(users, name);
        Role r = roles.findByName("SUPERVISOR").orElseGet(() -> roles.save(Role.builder().name("SUPERVISOR").build()));
        u.getRoles().add(r);
        return users.save(u);
    }

    private String device(String username) {
        String token = "tok-" + UUID.randomUUID() + "-xxxxxxxx";
        deviceService.register(username, new DeviceRegisterRequest(token, DevicePlatform.ANDROID, "1"));
        return token;
    }

    /** O listener e @Async: aguarda o envio assincrono. */
    private static void awaitSent(String token) {
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end && SENT.stream().noneMatch(x -> x.token().equals(token))) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Test
    void pendingApprovalGoesToSupervisorsExceptDeciderAndRespectsPreferenceAndRemovesInvalid() {
        SENT.clear();
        String decider = TestUsers.unique("sup-dec");
        String other = TestUsers.unique("sup-oth");
        String optOut = TestUsers.unique("sup-off");
        String bad = TestUsers.unique("sup-bad");
        User dUser = supervisor(decider);
        supervisor(other);
        supervisor(optOut);
        supervisor(bad);
        String tDecider = device(decider);
        String tOther = device(other);
        String tOff = device(optOut);
        String tBad = device(bad);
        deviceService.updatePreferences(optOut, new PushPreferencesDto(false, true));
        INVALID.add(tBad);

        UUID caseId = UUID.randomUUID();
        listener.onCasePendingApproval(new CasePendingApprovalEvent(caseId, dUser.getId()));

        awaitSent(tOther);
        List<String> tokens = SENT.stream().map(Sent::token).toList();
        assertTrue(tokens.contains(tOther));
        assertFalse(tokens.contains(tDecider), "quem decidiu nao recebe");
        assertFalse(tokens.contains(tOff), "preferencia desligada");
        awaitSent(tBad);
        for (int i = 0; i < 100 && devices.findByToken(tBad).isPresent(); i++) {
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        assertTrue(devices.findByToken(tBad).isEmpty(), "token invalido removido");
        SENT.stream().filter(s -> s.token().equals(tOther)).forEach(s -> {
            assertEquals(caseId, s.msg().id());
            assertEquals(PushMessage.ALLOWED_DATA_KEYS, s.msg().data().keySet());
        });
        INVALID.remove(tBad);
    }
}
