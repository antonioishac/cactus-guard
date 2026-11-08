package br.com.cactus.guard.kyc.job;

import br.com.cactus.guard.kyc.entity.KycProcess;
import br.com.cactus.guard.kyc.repository.KycProcessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KycExpirationJob {

    private final KycProcessRepository repository;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void expireOverdueProcesses() {
        Instant now = Instant.now();
        List<KycProcess> overdue = repository.findByStatusAndExpiresAtBefore("IN_PROGRESS", now);

        for (KycProcess process : overdue) {
            process.setStatus("EXPIRED");
            repository.save(process);
            log.info("KYC process {} expired for party {}", process.getId(), process.getParty().getId());
        }

        log.info("KYC expiration job completed. Expired: {}", overdue.size());
    }
}
