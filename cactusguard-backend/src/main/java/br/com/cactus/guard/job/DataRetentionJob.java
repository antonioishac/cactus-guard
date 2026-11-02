package br.com.cactus.guard.job;

import br.com.cactus.guard.audit.repository.AuditLogRepository;
import br.com.cactus.guard.audit.repository.DataAccessLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataRetentionJob {

    private final AuditLogRepository auditLogRepository;
    private final DataAccessLogRepository dataAccessLogRepository;

    @Scheduled(cron = "0 0 3 * * SUN")
    @Transactional
    public void purgeOldLogs() {
        Instant cutoff = Instant.now().minus(1825, ChronoUnit.DAYS); // 5 years

        long auditDeleted = auditLogRepository.deleteByCreatedAtBefore(cutoff);
        long accessDeleted = dataAccessLogRepository.deleteByCreatedAtBefore(cutoff);

        log.info("Data retention completed. Audit logs deleted: {}, Data access logs deleted: {}", 
                auditDeleted, accessDeleted);
    }
}
