package br.com.cactus.guard.alert.service;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.common.event.CriticalAlertCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import br.com.cactus.guard.alert.entity.AlertTransaction;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.alert.repository.AlertTransactionRepository;
import br.com.cactus.guard.common.calendar.RegulatoryDeadlines;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.transaction.entity.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ponto unico de criacao de alertas: garante occurredAt, selectionDeadline (+45 dias),
 * deduplicacao e vinculo com as transacoes que originaram o alerta.
 */
@Service
@RequiredArgsConstructor
public class AlertCreationService {

    private static final List<String> LEVELS = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final AlertRepository alertRepository;
    private final AlertTransactionRepository alertTransactionRepository;
    private final RegulatoryDeadlines deadlines;
    private final ApplicationEventPublisher eventPublisher;

    /** Dados de um alerta a ser criado. */
    public record AlertDraft(Party party,
                             MonitoringRule rule,
                             String type,
                             String severity,
                             String title,
                             String description,
                             String ruleCode,
                             String dedupKey,
                             BigDecimal totalAmount,
                             Instant occurredAt,
                             Map<String, Object> evidence,
                             List<Transaction> transactions) {
    }

    /** Cria o alerta, exceto se ja existir alerta (em qualquer status) com a mesma dedupKey. */
    @Transactional
    public Optional<Alert> createIfAbsent(AlertDraft d) {
        if (d.dedupKey() != null && alertRepository.existsByDedupKey(d.dedupKey())) {
            return Optional.empty();
        }
        return Optional.of(create(d));
    }

    @Transactional
    public Alert create(AlertDraft d) {
        List<Transaction> txns = d.transactions() == null ? List.of() : d.transactions();
        Instant occurredAt = d.occurredAt();
        if (occurredAt == null) {
            occurredAt = txns.stream().map(Transaction::getTransactionDate)
                    .filter(java.util.Objects::nonNull)
                    .min(Comparator.naturalOrder()).orElse(Instant.now());
        }
        String severity = d.severity() != null ? d.severity() : (d.rule() != null ? d.rule().getSeverity() : "MEDIUM");
        Alert alert = Alert.builder()
                .party(d.party())
                .type(d.type() != null ? d.type() : (d.rule() != null ? d.rule().getCategory() : "GENERAL"))
                .severity(severity)
                .status("OPEN")
                .title(d.title())
                .description(d.description())
                .ruleCode(d.ruleCode() != null ? d.ruleCode() : (d.rule() != null ? d.rule().getCode() : null))
                .rule(d.rule())
                .ruleVersion(d.rule() != null ? d.rule().getVersion() : null)
                .score(scoreFor(severity))
                .totalAmount(d.totalAmount())
                .dedupKey(d.dedupKey())
                .evidence(d.evidence())
                .occurredAt(occurredAt)
                .selectionDeadline(deadlines.selectionDeadline(occurredAt))
                .build();
        alert = alertRepository.save(alert);
        if ("CRITICAL".equals(alert.getSeverity())) {
            eventPublisher.publishEvent(new CriticalAlertCreatedEvent(alert.getId()));
        }
        for (Transaction t : txns) {
            alertTransactionRepository.save(AlertTransaction.builder().alert(alert).transaction(t).build());
        }
        return alert;
    }

    /** Severidade agravada em um nivel (teto CRITICAL). */
    public static String escalate(String severity) {
        int i = LEVELS.indexOf(severity);
        if (i < 0) return severity;
        return LEVELS.get(Math.min(i + 1, LEVELS.size() - 1));
    }

    static BigDecimal scoreFor(String severity) {
        return switch (severity) {
            case "CRITICAL" -> BigDecimal.valueOf(100);
            case "HIGH" -> BigDecimal.valueOf(75);
            case "MEDIUM" -> BigDecimal.valueOf(50);
            default -> BigDecimal.valueOf(25);
        };
    }
}
