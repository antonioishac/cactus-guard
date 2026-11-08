package br.com.cactus.guard.siscoaf.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class SiscoafResponse {
    private UUID id;
    private UUID caseId;
    /** Numero legivel do case de origem. */
    private String caseNumber;
    private UUID partyId;
    private String partyName;
    private UUID transactionId;
    private UUID originalCommunicationId;
    private String type;
    private String status;
    private String protocolNumber;
    private Instant submittedAt;
    private Instant deadlineAt;
    private Instant decidedAt;
    private Integer referenceYear;
    private BigDecimal amount;
    /** Codigos de indicios (Carta Circular 4.001). */
    private List<String> indicatorCodes;
    /** Periodo das operacoes comunicadas (do case de origem). */
    private LocalDate periodFrom;
    private LocalDate periodTo;
    private Instant cancelledAt;
    private String cancelReason;
    private UUID createdById;
    private UUID approvedById;
    private Instant approvedAt;
    /** Quatro olhos exigido (PEP, risco alto/critico ou EDD): aprovador diferente do criador e do decisor. */
    private boolean fourEyesRequired;
    private Map<String, Object> payload;
    private Instant createdAt;
    /** Somente no detalhe (GET /siscoaf/{id}); null na listagem. */
    private List<SiscoafHistoryEntry> statusHistory;
}
