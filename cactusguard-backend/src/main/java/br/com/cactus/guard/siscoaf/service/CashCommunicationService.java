package br.com.cactus.guard.siscoaf.service;

import br.com.cactus.guard.common.audit.AuditPayload;
import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.common.calendar.BusinessCalendar;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import br.com.cactus.guard.siscoaf.entity.SiscoafCommunication;
import br.com.cactus.guard.siscoaf.repository.SiscoafCommunicationRepository;
import br.com.cactus.guard.transaction.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Comunicacao automatica de operacao em especie (COE) - Circular BCB 3.978/2020.
 * <p>
 * Excecao legal: deposito/saque/aporte/provisionamento de saque em especie igual ou superior a
 * R$ 50.000,00 e comunicado independentemente de analise de suspeita, portanto nao passa por
 * decisao humana de "suspeita". O registro e criado como APPROVED (pronto para envio) e o
 * envio ao COAF continua sendo acao explicita; a criacao e sempre auditada.
 * A obrigacao legal vale mesmo que a regra CASH_50K ainda nao esteja aprovada: o limiar so e
 * lido da regra quando ela esta ACTIVE; caso contrario usa o valor legal (R$ 50.000).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CashCommunicationService {

    public static final BigDecimal LEGAL_THRESHOLD = new BigDecimal("50000");
    public static final String RULE_CODE = "CASH_50K";
    private static final List<String> INACTIVE_STATUSES = List.of("CANCELLED", "RECTIFIED", "REJECTED");

    private final SiscoafCommunicationRepository repository;
    private final MonitoringRuleRepository ruleRepository;
    private final AuditService auditService;
    private final BusinessCalendar calendar;

    /** Limiar vigente: parametro da regra CASH_50K quando ACTIVE; senao o limiar legal. */
    public BigDecimal currentThreshold() {
        return ruleRepository.findByCode(RULE_CODE)
                .filter(r -> "ACTIVE".equals(r.getStatus()) && r.isActive())
                .map(this::thresholdOf)
                .orElse(LEGAL_THRESHOLD);
    }

    private BigDecimal thresholdOf(MonitoringRule r) {
        Object v = r.getParameters() != null ? r.getParameters().get("threshold_brl") : null;
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return r.getThresholdValue() != null ? r.getThresholdValue() : LEGAL_THRESHOLD;
    }

    /** Valor da operacao em reais; vazio quando nao ha como converter (moeda estrangeira sem amountBrl). */
    public static Optional<BigDecimal> amountInBrl(Transaction t) {
        if (t.getAmountBrl() != null) {
            return Optional.of(t.getAmountBrl());
        }
        if (t.getCurrency() == null || "BRL".equals(t.getCurrency())) {
            return Optional.ofNullable(t.getAmount());
        }
        return Optional.empty();
    }

    public static boolean isCashOperation(Transaction t) {
        return t.isCash() || t.isCashWithdrawalProvision();
    }

    /** Fluxo de ingestao: cria a COE se a operacao atende aos criterios. */
    @Transactional
    public Optional<SiscoafCommunication> evaluate(Transaction txn) {
        return evaluate(txn, currentThreshold());
    }

    @Transactional
    public Optional<SiscoafCommunication> evaluate(Transaction txn, BigDecimal threshold) {
        if (!isCashOperation(txn)) {
            return Optional.empty();
        }
        Optional<BigDecimal> amount = amountInBrl(txn);
        if (amount.isEmpty()) {
            log.warn("Operacao em especie {} em moeda {} sem amount_brl: COE nao avaliada, requer conversao manual",
                    txn.getId(), txn.getCurrency());
            return Optional.empty();
        }
        if (amount.get().compareTo(threshold) < 0) {
            return Optional.empty();
        }
        if (repository.existsByTransactionIdAndTypeAndStatusNotIn(txn.getId(), "COE", INACTIVE_STATUSES)) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        Instant operation = txn.getTransactionDate() != null ? txn.getTransactionDate() : now;
        Instant deadline = calendar.nextBusinessDayDeadline(operation);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionId", txn.getId().toString());
        payload.put("partyId", txn.getParty().getId().toString());
        payload.put("operationDate", operation.toString());
        payload.put("amountBrl", amount.get());
        payload.put("direction", txn.getDirection());
        payload.put("cashWithdrawalProvision", txn.isCashWithdrawalProvision());

        SiscoafCommunication coe = SiscoafCommunication.builder()
                .party(txn.getParty())
                .transaction(txn)
                .type("COE")
                .status("APPROVED")
                .amount(amount.get())
                .decidedAt(now)
                .deadlineAt(deadline)
                .payload(payload)
                .build();
        coe = repository.save(coe);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("type", "COE");
        audit.put("transactionId", txn.getId().toString());
        audit.put("amountBrl", amount.get());
        audit.put("threshold", threshold);
        audit.put("deadlineAt", deadline.toString());
        audit.put("basis", "Comunicacao automatica de especie >= limiar (excecao legal: dispensa decisao de suspeita)");
        AuditPayload.log(auditService, "COE_AUTO_CREATED", "SISCOAF_COMMUNICATION", coe.getId(), null, audit);
        return Optional.of(coe);
    }
}
