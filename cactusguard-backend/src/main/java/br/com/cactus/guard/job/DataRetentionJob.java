package br.com.cactus.guard.job;

import br.com.cactus.guard.audit.repository.AuditLogRepository;
import br.com.cactus.guard.audit.repository.DataAccessLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import br.com.cactus.guard.tenant.service.TenantRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Expurgo de logs de auditoria. Circular BCB 3.978/2020, art. 67: guarda minima de 10 anos
 * (alinhado ao trigger append-only da V22, que so permite DELETE &gt; 10 anos). O prazo e
 * configuravel ({@code retention.audit-days}) mas nunca abaixo de {@value #MIN_RETENTION_DAYS} dias:
 * valores menores impedem a inicializacao.
 */
@Slf4j
@Component
public class DataRetentionJob {

    public static final long MIN_RETENTION_DAYS = 3653; // 10 anos

    private final AuditLogRepository auditLogRepository;
    private final DataAccessLogRepository dataAccessLogRepository;
    private final long retentionDays;

    public DataRetentionJob(AuditLogRepository auditLogRepository,
                            DataAccessLogRepository dataAccessLogRepository,
                            @Value("${retention.audit-days:3653}") long retentionDays) {
        if (retentionDays < MIN_RETENTION_DAYS) {
            throw new IllegalStateException("retention.audit-days (" + retentionDays
                    + ") abaixo do minimo regulatorio de " + MIN_RETENTION_DAYS + " dias (10 anos)");
        }
        this.auditLogRepository = auditLogRepository;
        this.dataAccessLogRepository = dataAccessLogRepository;
        this.retentionDays = retentionDays;
    }

    public long getRetentionDays() {
        return retentionDays;
    }

    @Autowired
    private TenantRunner tenantRunner;

    @Autowired
    private TransactionTemplate tx;

    /** Expurgo por tenant: audit_log e data_access_log sao do tenant (e protegidos por RLS). */
    @Scheduled(cron = "0 0 3 * * SUN")
    public void purgeOldLogs() {
        tenantRunner.forEachActiveTenant("DataRetentionJob", tenantId -> tx.executeWithoutResult(s -> purgeCurrentTenant()));
    }

    /** Roda dentro de transacao e do tenant corrente. */
    void purgeCurrentTenant() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        long auditDeleted = auditLogRepository.deleteByCreatedAtBefore(cutoff);
        long accessDeleted = dataAccessLogRepository.deleteByCreatedAtBefore(cutoff);

        log.info("Data retention completed (cutoff {} dias). Audit logs deleted: {}, Data access logs deleted: {}",
                retentionDays, auditDeleted, accessDeleted);
    }
}
