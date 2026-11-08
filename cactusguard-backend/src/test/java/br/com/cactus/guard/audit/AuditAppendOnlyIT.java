package br.com.cactus.guard.audit;

import br.com.cactus.guard.audit.repository.AuditLogRepository;
import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** audit_log tem trigger append-only: gravar BigDecimal/Instant/Long nao pode gerar UPDATE espurio. */
class AuditAppendOnlyIT extends AbstractPostgresIT {

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditLogRepository repository;

    @Autowired
    private TransactionTemplate tx;

    @Test
    void auditWithBigDecimalAndInstantCommitsWithoutUpdate() {
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(s -> {
            auditService.log("TEST", "PARTY", id,
                    Map.of("amount", new BigDecimal("10.50"), "at", Instant.now()),
                    Map.of("amount", new BigDecimal("20.00"), "n", 5L, "list", List.of(new BigDecimal("1.0"))));
            repository.flush();
        });
        assertEquals(1, repository.findByEntityTypeAndEntityId("PARTY", id,
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements());
    }
}
