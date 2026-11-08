package br.com.cactus.guard.party.job;

import br.com.cactus.guard.common.calendar.BusinessCalendar;
import br.com.cactus.guard.party.service.PartyPepService;
import br.com.cactus.guard.tenant.service.TenantRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Job diario: expira a condicao de PEP vencida (fim do cargo + 5 anos), tenant a tenant. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PepExpiryJob {

    private final PartyPepService pepService;
    private final TenantRunner tenantRunner;

    @Scheduled(cron = "0 15 1 * * *", zone = "America/Sao_Paulo")
    public void run() {
        LocalDate today = LocalDate.now(BusinessCalendar.BRAZIL);
        tenantRunner.forEachActiveTenant("PepExpiryJob", tenantId -> {
            int expired = pepService.expireDue(today);
            log.info("PepExpiryJob concluido: {} PEP expirado(s)", expired);
        });
    }
}
