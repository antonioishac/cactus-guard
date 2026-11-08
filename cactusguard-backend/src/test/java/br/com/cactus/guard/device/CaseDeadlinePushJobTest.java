package br.com.cactus.guard.device;

import br.com.cactus.guard.common.calendar.BusinessCalendar;
import br.com.cactus.guard.device.push.PushRecipientResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Garante que as consultas JPQL do resolver sao validas contra o modelo (H2). */
@SpringBootTest
@ActiveProfiles("test")
class CaseDeadlinePushJobTest {

    @Autowired PushRecipientResolver resolver;

    @Test
    void queriesAreValid() {
        assertNotNull(resolver.dueCases(Instant.now().plusSeconds(86400), BusinessCalendar.endOfDay(LocalDate.now()), 10));
        assertNotNull(resolver.alertAssignee(UUID.randomUUID()));
    }
}
