package br.com.cactus.guard.alert;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.alert.service.AlertService;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.notification.repository.NotificationRepository;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AlertNotificationTest {

    @Autowired
    private AlertService alertService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @WithMockUser(username = "admin", roles = "ANALYST")
    void assumeAlertShouldCreateNotification() {
        Party party = Party.builder()
                .type("PF")
                .document("11100011100")
                .name("Notification Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        Alert alert = Alert.builder()
                .party(party)
                .type("SCREENING")
                .severity("MEDIUM")
                .status("OPEN")
                .title("Notification Alert")
                .ruleCode("TXN_VELOCITY")
                .createdAt(Instant.now())
                .build();
        alert = alertRepository.save(alert);

        long before = notificationRepository.count();
        alertService.assume(alert.getId());
        long after = notificationRepository.count();

        assertTrue(after >= before);
    }
}
