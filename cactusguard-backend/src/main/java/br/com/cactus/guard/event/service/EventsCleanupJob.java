package br.com.cactus.guard.event.service;

import br.com.cactus.guard.tenant.service.TenantRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Agendador diario do expurgo (por tenant). */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cactus.events", name = "enabled", havingValue = "true")
public class EventsCleanupJob {

    private final EventsCleanupService cleanupService;
    private final TenantRunner tenantRunner;

    @Scheduled(cron = "${cactus.events.cleanup.cron:0 30 3 * * *}")
    public void run() {
        tenantRunner.forEachActiveTenant("EventsCleanup", tenantId -> cleanupService.cleanupCurrentTenant());
    }
}
