package br.com.cactus.guard.siscoaf.service;

import br.com.cactus.guard.case_.entity.Case;
import br.com.cactus.guard.siscoaf.entity.SiscoafCommunication;
import br.com.cactus.guard.siscoaf.repository.SiscoafCommunicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cria o rascunho de COS a partir de um case com decisao COMMUNICATE aprovada.
 * O rascunho NUNCA e enviado automaticamente: o envio e acao explicita de SUPERVISOR.
 * O payload referencia a parte por id (sem PII em claro).
 */
@Service
@RequiredArgsConstructor
public class SiscoafDraftService {

    private final SiscoafCommunicationRepository repository;

    @Transactional
    public SiscoafCommunication createCosDraft(Case c, Instant decidedAt, Instant deadline) {
        List<SiscoafCommunication> existing = repository.findByCaseEntityIdAndType(c.getId(), "COS");
        for (SiscoafCommunication e : existing) {
            if (!"CANCELLED".equals(e.getStatus()) && !"REJECTED".equals(e.getStatus())
                    && !"RECTIFIED".equals(e.getStatus())) {
                // rascunho manual preexistente: passa a refletir a decisao aprovada e o prazo regulatorio
                if (e.getDecidedAt() == null && "DRAFT".equals(e.getStatus())) {
                    e.setDecidedAt(decidedAt);
                    e.setDeadlineAt(deadline);
                    return repository.save(e);
                }
                return e;
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", c.getId().toString());
        if (c.getParty() != null) {
            payload.put("partyId", c.getParty().getId().toString());
        }
        payload.put("justification", c.getDecisionJustification());
        if (c.getIndicatorCodes() != null) {
            payload.put("indicatorCodes", c.getIndicatorCodes());
        }
        if (c.getTotalAmountInvolved() != null) {
            payload.put("totalAmountInvolved", c.getTotalAmountInvolved());
        }
        SiscoafCommunication comm = SiscoafCommunication.builder()
                .caseEntity(c)
                .party(c.getParty())
                .type("COS")
                .status("DRAFT")
                .payload(payload)
                .decidedAt(decidedAt)
                .deadlineAt(deadline)
                .amount(c.getTotalAmountInvolved())
                .indicatorCodes(c.getIndicatorCodes())
                .createdBy(c.getApprovedBy() != null ? c.getApprovedBy() : c.getDecidedBy())
                .build();
        return repository.save(comm);
    }
}
