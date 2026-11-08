package br.com.cactus.guard.party.service;

import br.com.cactus.guard.common.audit.AuditPayload;
import br.com.cactus.guard.alert.service.AlertCreationService;
import br.com.cactus.guard.alert.service.AlertCreationService.AlertDraft;
import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.common.calendar.BusinessCalendar;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.common.security.CurrentUserService;
import br.com.cactus.guard.common.validation.Justifications;
import br.com.cactus.guard.party.dto.PepRequest;
import br.com.cactus.guard.party.dto.PepResponse;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.rule.engine.RiskScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PEP (Circular BCB 3.978/2020, arts. 19, 27 e 34): condicao dura ate 5 anos apos o fim do cargo;
 * marcar PEP forca diligencia reforcada (EDD) e risco minimo HIGH; manter relacionamento exige
 * aprovacao da alta administracao (SUPERVISOR).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartyPepService {

    public static final int PEP_YEARS_AFTER_END = 5;

    private final PartyRepository partyRepository;
    private final RiskScoringService riskScoring;
    private final AuditService auditService;
    private final CurrentUserService currentUser;
    private final AlertCreationService alertCreation;

    /** Vigencia da condicao de PEP: fim do cargo + 5 anos (nulo enquanto no cargo). */
    public static LocalDate expiryDate(LocalDate endDate) {
        return endDate == null ? null : endDate.plusYears(PEP_YEARS_AFTER_END);
    }

    @Transactional
    public PepResponse markPep(UUID partyId, PepRequest req) {
        String just = Justifications.require(req.justification(), "marcar parte como PEP");
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new BusinessException("Parte não encontrada"));
        if (req.startDate() != null && req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw new BusinessException("Data de fim do cargo anterior ao início");
        }

        Map<String, Object> before = pepState(party);
        boolean wasPep = party.isPep();
        LocalDate today = LocalDate.now(BusinessCalendar.BRAZIL);
        LocalDate expiry = expiryDate(req.endDate());

        party.setPep(true);
        party.setPepCategory(req.category());
        party.setPepRole(req.role());
        party.setPepStartDate(req.startDate());
        party.setPepEndDate(req.endDate());
        party.setPepExpiryDate(expiry);
        if (expiry != null && expiry.isBefore(today)) {
            party.setPepStatus("EXPIRED");
        } else {
            party.setPepStatus(req.related() ? "RELATED" : "ACTIVE");
        }
        if (!wasPep) {
            // nova condicao de PEP: aprovacao anterior (se houver) nao se aplica
            party.setPepApprovalBy(null);
            party.setPepApprovalAt(null);
        }
        party.setEddRequired(true);
        partyRepository.save(party);
        // piso HIGH e EDD vem do calculo de risco (fator PEP)
        riskScoring.assessAndApply(party, "Marcação PEP");

        Map<String, Object> after = pepState(party);
        after.put("justification", just);
        AuditPayload.log(auditService, "PEP_MARKED", "PARTY", party.getId(), before, after);
        return toResponse(party);
    }

    @Transactional(readOnly = true)
    public PepResponse get(UUID partyId) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new br.com.cactus.guard.common.exception.NotFoundException("Parte não encontrada"));
        return toResponse(party);
    }

    /** Aprovacao da alta administracao para iniciar/manter relacionamento com PEP. */
    @Transactional
    public PepResponse approvePep(UUID partyId, String comment) {
        if (!currentUser.hasRole("SUPERVISOR")) {
            throw new AccessDeniedException("Aprovação de PEP exige perfil SUPERVISOR");
        }
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new BusinessException("Parte não encontrada"));
        if (!party.isPep()) {
            throw new BusinessException("Parte não está marcada como PEP");
        }
        User approver = currentUser.user()
                .orElseThrow(() -> new BusinessException("Aprovador não identificado"));
        Map<String, Object> before = pepState(party);
        party.setPepApprovalBy(approver);
        party.setPepApprovalAt(Instant.now());
        partyRepository.save(party);
        Map<String, Object> after = pepState(party);
        if (comment != null && !comment.isBlank()) after.put("comment", comment.trim());
        AuditPayload.log(auditService, "PEP_APPROVED", "PARTY", party.getId(), before, after);
        return toResponse(party);
    }

    /** Job diario: ACTIVE vencido vira EXPIRED e gera alerta de mudanca de status para analise humana. */
    @Transactional
    public int expireDue(LocalDate today) {
        int count = 0;
        for (Party party : partyRepository.findByPepStatusAndPepExpiryDateBefore("ACTIVE", today)) {
            Map<String, Object> before = pepState(party);
            party.setPepStatus("EXPIRED");
            partyRepository.save(party);
            Map<String, Object> after = pepState(party);
            AuditPayload.log(auditService, "PEP_EXPIRED", "PARTY", party.getId(), before, after);

            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("previousStatus", "ACTIVE");
            evidence.put("newStatus", "EXPIRED");
            evidence.put("expiryDate", String.valueOf(party.getPepExpiryDate()));
            alertCreation.createIfAbsent(new AlertDraft(party, null, "PEP", "MEDIUM",
                    "PEP: mudança de status (expirado)",
                    "Condição de PEP da parte " + party.getName() + " expirou em " + party.getPepExpiryDate()
                            + ". Revisar nível de risco e diligência.",
                    "PEP_STATUS_CHANGE", "PEP_STATUS_CHANGE:" + party.getId() + ":EXPIRED",
                    null, null, evidence, List.of()));
            count++;
        }
        log.info("PEP expiry job: {} parties expired", count);
        return count;
    }

    private Map<String, Object> pepState(Party p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pep", p.isPep());
        m.put("pepStatus", p.getPepStatus());
        m.put("pepCategory", p.getPepCategory());
        m.put("pepEndDate", p.getPepEndDate() != null ? p.getPepEndDate().toString() : null);
        m.put("pepExpiryDate", p.getPepExpiryDate() != null ? p.getPepExpiryDate().toString() : null);
        m.put("eddRequired", p.isEddRequired());
        m.put("riskLevel", p.getRiskLevel());
        m.put("pepApprovalBy", p.getPepApprovalBy() != null ? p.getPepApprovalBy().getId().toString() : null);
        return m;
    }

    private PepResponse toResponse(Party p) {
        return new PepResponse(p.getId(), p.isPep(), p.getPepStatus(), p.getPepCategory(), p.getPepRole(),
                p.getPepStartDate(), p.getPepEndDate(), p.getPepExpiryDate(),
                p.getPepApprovalBy() != null, p.isEddRequired(), p.getRiskLevel(),
                p.getPepApprovalAt(), p.getPepApprovalBy() != null ? p.getPepApprovalBy().getName() : null);
    }
}
