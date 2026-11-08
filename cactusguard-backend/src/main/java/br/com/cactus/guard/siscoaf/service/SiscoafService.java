package br.com.cactus.guard.siscoaf.service;

import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.case_.entity.Case;
import br.com.cactus.guard.case_.repository.CaseRepository;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.common.security.CurrentUserService;
import br.com.cactus.guard.common.validation.Justifications;
import br.com.cactus.guard.integration.siscoaf.SiscoafOrchestrator;
import br.com.cactus.guard.siscoaf.dto.SiscoafResponse;
import br.com.cactus.guard.siscoaf.entity.SiscoafCommunication;
import br.com.cactus.guard.siscoaf.repository.SiscoafCommunicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SiscoafService {

    private static final Set<String> DRAFT_TYPES = Set.of("COS", "COE", "SAR", "COAF_COMMUNICATION");
    private static final Set<String> CANCELLABLE = Set.of("DRAFT", "PENDING_REVIEW", "APPROVED");
    private static final Set<String> SENT = Set.of("SUBMITTED", "ACKNOWLEDGED");

    private final SiscoafCommunicationRepository repository;
    private final CaseRepository caseRepository;
    private final SiscoafOrchestrator orchestrator;
    private final AuditService auditService;
    private final CurrentUserService currentUser;

    @Transactional
    public SiscoafResponse createDraft(UUID caseId, String type, Map<String, Object> payload) {
        if (type == null || !DRAFT_TYPES.contains(type)) {
            throw new BusinessException("Tipo de comunicacao invalido: " + type
                    + " (declaracao negativa possui endpoint proprio)");
        }
        Case caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found"));

        SiscoafCommunication comm = SiscoafCommunication.builder()
                .caseEntity(caseEntity)
                .party(caseEntity.getParty())
                .type(type)
                .status("DRAFT")
                .payload(payload)
                .createdBy(currentUser.user().orElse(null))
                .build();

        comm = repository.save(comm);
        auditService.log("SISCOAF_DRAFT_CREATED", "SISCOAF_COMMUNICATION", comm.getId(), null,
                Map.of("type", type, "caseId", caseId.toString()));
        return toResponse(comm);
    }

    /**
     * Envio ao COAF: acao explicita de SUPERVISOR. COS/SAR exigem case com decisao COMMUNICATE
     * aprovada (decisao humana); nada e enviado automaticamente.
     */
    @Transactional
    public SiscoafResponse submit(UUID id) {
        SiscoafCommunication comm = repository.findById(id)
                .orElseThrow(() -> new BusinessException("Communication not found"));

        if (!"APPROVED".equals(comm.getStatus()) && !"DRAFT".equals(comm.getStatus())) {
            throw new BusinessException("Cannot submit in status: " + comm.getStatus());
        }
        Case linked = comm.getCaseEntity();
        if ("COS".equals(comm.getType()) || "SAR".equals(comm.getType())) {
            boolean decided = linked != null
                    && ("COMMUNICATE".equals(linked.getStatus()) || "COMMUNICATED".equals(linked.getStatus()));
            if (!decided) {
                throw new BusinessException(
                        "COS so pode ser enviada apos decisao humana COMMUNICATE aprovada no case");
            }
        }

        String fromStatus = comm.getStatus();
        Instant now = Instant.now();
        comm.setApprovedBy(currentUser.user().orElse(comm.getApprovedBy()));
        comm.setApprovedAt(now);
        comm = orchestrator.submit(comm);
        comm.setSubmittedAt(now);
        comm = repository.save(comm);

        if (linked != null && "COMMUNICATE".equals(linked.getStatus())) {
            linked.setStatus("COMMUNICATED");
            linked.setClosedAt(now);
            caseRepository.save(linked);
        }

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("status", comm.getStatus());
        audit.put("protocol", comm.getProtocolNumber());
        audit.put("late", comm.getDeadlineAt() != null && now.isAfter(comm.getDeadlineAt()));
        auditService.log("SISCOAF_SUBMIT", "SISCOAF_COMMUNICATION", comm.getId(),
                Map.of("status", fromStatus), audit);
        return toResponse(comm);
    }

    @Transactional
    public SiscoafResponse cancel(UUID id, String reason) {
        String just = Justifications.require(reason, "cancelar comunicacao");
        SiscoafCommunication comm = repository.findById(id)
                .orElseThrow(() -> new BusinessException("Communication not found"));
        if (!CANCELLABLE.contains(comm.getStatus())) {
            throw new BusinessException("Comunicacao em status " + comm.getStatus()
                    + " nao pode ser cancelada (use retificacao)");
        }
        String from = comm.getStatus();
        comm.setStatus("CANCELLED");
        comm.setCancelledAt(Instant.now());
        comm.setCancelReason(just);
        comm = repository.save(comm);
        auditService.log("SISCOAF_CANCEL", "SISCOAF_COMMUNICATION", comm.getId(),
                Map.of("status", from), Map.of("status", "CANCELLED", "reason", just));
        return toResponse(comm);
    }

    /** Retifica comunicacao ja enviada: marca a original como RECTIFIED e cria novo rascunho vinculado. */
    @Transactional
    public SiscoafResponse rectify(UUID id, String reason, Map<String, Object> newPayload) {
        String just = Justifications.require(reason, "retificar comunicacao");
        SiscoafCommunication original = repository.findById(id)
                .orElseThrow(() -> new BusinessException("Communication not found"));
        if (!SENT.contains(original.getStatus())) {
            throw new BusinessException("So comunicacoes enviadas podem ser retificadas");
        }
        original.setStatus("RECTIFIED");
        repository.saveAndFlush(original);

        SiscoafCommunication copy = SiscoafCommunication.builder()
                .caseEntity(original.getCaseEntity())
                .party(original.getParty())
                .type(original.getType())
                .status("DRAFT")
                .payload(newPayload != null ? newPayload : original.getPayload())
                .deadlineAt(original.getDeadlineAt())
                .decidedAt(original.getDecidedAt())
                .referenceYear(original.getReferenceYear())
                .amount(original.getAmount())
                .indicatorCodes(original.getIndicatorCodes())
                .transaction(original.getTransaction())
                .originalCommunication(original)
                .createdBy(currentUser.user().orElse(null))
                .build();
        copy = repository.save(copy);
        auditService.log("SISCOAF_RECTIFY", "SISCOAF_COMMUNICATION", original.getId(),
                Map.of("status", "SUBMITTED"),
                Map.of("status", "RECTIFIED", "newCommunicationId", copy.getId().toString(), "reason", just));
        return toResponse(copy);
    }

    @Transactional(readOnly = true)
    public Page<SiscoafResponse> findByStatus(String status, Pageable pageable) {
        return repository.findByStatus(status, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public SiscoafResponse findById(UUID id) {
        SiscoafCommunication comm = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Communication not found"));
        return toResponse(comm);
    }

    public static SiscoafResponse toResponse(SiscoafCommunication c) {
        return SiscoafResponse.builder()
                .id(c.getId())
                .caseId(c.getCaseEntity() != null ? c.getCaseEntity().getId() : null)
                .partyId(c.getParty() != null ? c.getParty().getId() : null)
                .transactionId(c.getTransaction() != null ? c.getTransaction().getId() : null)
                .originalCommunicationId(c.getOriginalCommunication() != null ? c.getOriginalCommunication().getId() : null)
                .type(c.getType())
                .status(c.getStatus())
                .protocolNumber(c.getProtocolNumber())
                .submittedAt(c.getSubmittedAt())
                .deadlineAt(c.getDeadlineAt())
                .decidedAt(c.getDecidedAt())
                .referenceYear(c.getReferenceYear())
                .amount(c.getAmount())
                .cancelledAt(c.getCancelledAt())
                .payload(c.getPayload())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
