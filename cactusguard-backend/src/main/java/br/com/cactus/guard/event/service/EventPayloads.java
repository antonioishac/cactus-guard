package br.com.cactus.guard.event.service;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.case_.entity.Case;
import br.com.cactus.guard.siscoaf.entity.SiscoafCommunication;
import br.com.cactus.guard.transaction.entity.Transaction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Montagem do {@code data} de cada evento do catalogo. REGRA: somente ids, enums e valores nao pessoais (valores
 * monetarios e datas como texto, sem perda de precisao). Nada de nome, documento, e-mail, telefone, endereco,
 * justificativas ou descricoes livres (podem conter PII): o consumidor busca o restante no banco, autenticado.
 */
public final class EventPayloads {

    private EventPayloads() {
    }

    public static Map<String, Object> transactionReceived(Transaction t) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "transactionId", t.getId());
        put(m, "partyId", t.getParty().getId());
        put(m, "type", t.getType());
        put(m, "direction", t.getDirection());
        put(m, "amount", t.getAmount());
        put(m, "currency", t.getCurrency());
        put(m, "amountBrl", t.getAmountBrl());
        put(m, "channel", t.getChannel());
        m.put("cash", t.isCash());
        put(m, "status", t.getStatus());
        put(m, "transactionDate", t.getTransactionDate());
        return m;
    }

    public static Map<String, Object> alertCreated(Alert a) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "alertId", a.getId());
        put(m, "partyId", a.getParty() != null ? a.getParty().getId() : null);
        put(m, "type", a.getType());
        put(m, "severity", a.getSeverity());
        put(m, "ruleCode", a.getRuleCode());
        put(m, "ruleVersion", a.getRuleVersion());
        put(m, "totalAmount", a.getTotalAmount());
        put(m, "occurredAt", a.getOccurredAt());
        put(m, "selectionDeadline", a.getSelectionDeadline());
        return m;
    }

    public static Map<String, Object> caseEvent(Case c) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "caseId", c.getId());
        put(m, "caseNumber", c.getCaseNumber());
        put(m, "partyId", c.getParty() != null ? c.getParty().getId() : null);
        put(m, "status", c.getStatus());
        put(m, "decision", c.getDecision());
        put(m, "decidedAt", c.getDecidedAt());
        put(m, "approvedAt", c.getApprovedAt());
        put(m, "communicationDeadline", c.getCommunicationDeadline());
        return m;
    }

    public static Map<String, Object> communication(SiscoafCommunication c) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "communicationId", c.getId());
        put(m, "type", c.getType());
        put(m, "status", c.getStatus());
        put(m, "caseId", c.getCaseEntity() != null ? c.getCaseEntity().getId() : null);
        put(m, "partyId", c.getParty() != null ? c.getParty().getId() : null);
        put(m, "transactionId", c.getTransaction() != null ? c.getTransaction().getId() : null);
        put(m, "deadlineAt", c.getDeadlineAt());
        put(m, "submittedAt", c.getSubmittedAt());
        return m;
    }

    public static Map<String, Object> partyRiskChanged(UUID partyId, String previousLevel, String newLevel,
                                                       Object previousScore, Object newScore, String trigger) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "partyId", partyId);
        put(m, "previousLevel", previousLevel);
        put(m, "newLevel", newLevel);
        put(m, "previousScore", previousScore);
        put(m, "newScore", newScore);
        put(m, "trigger", trigger);
        return m;
    }

    public static Map<String, Object> assetFreezeActivated(UUID partyId, String reference, UUID screeningResultId,
                                                           String listCode) {
        Map<String, Object> m = new LinkedHashMap<>();
        put(m, "partyId", partyId);
        put(m, "reference", reference);
        put(m, "screeningResultId", screeningResultId);
        put(m, "listCode", listCode);
        return m;
    }

    /** Valores viram String (precisao de BigDecimal, UUID, Instant ISO-8601); numeros inteiros e booleanos ficam. */
    private static void put(Map<String, Object> m, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Integer || value instanceof Boolean || value instanceof String) {
            m.put(key, value);
        } else if (value instanceof java.math.BigDecimal bd) {
            m.put(key, bd.toPlainString());
        } else {
            m.put(key, value.toString());
        }
    }
}
