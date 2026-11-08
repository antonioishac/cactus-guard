package br.com.cactus.guard.case_.service;

import br.com.cactus.guard.alert.dto.PartySummary;
import br.com.cactus.guard.alert.dto.TransactionSummary;
import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertTransactionRepository;
import br.com.cactus.guard.alert.service.AlertService;
import br.com.cactus.guard.audit.repository.AuditLogRepository;
import br.com.cactus.guard.audit.service.DataAccessService;
import br.com.cactus.guard.case_.dto.CaseDossierResponse;
import br.com.cactus.guard.case_.dto.CaseDossierResponse.AlertInfo;
import br.com.cactus.guard.case_.dto.CaseDossierResponse.AuditItem;
import br.com.cactus.guard.case_.dto.CaseDossierResponse.PepInfo;
import br.com.cactus.guard.case_.dto.CaseDossierResponse.PeriodWindow;
import br.com.cactus.guard.case_.dto.CaseDossierResponse.RiskHistoryItem;
import br.com.cactus.guard.case_.dto.CaseDossierResponse.ScreeningHitItem;
import br.com.cactus.guard.case_.dto.CaseDossierResponse.UboItem;
import br.com.cactus.guard.case_.entity.Case;
import br.com.cactus.guard.case_.repository.CaseDossierQueryRepository;
import br.com.cactus.guard.case_.repository.CaseRepository;
import br.com.cactus.guard.common.calendar.BusinessCalendar;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.common.util.DataMasker;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.BeneficialOwnerRepository;
import br.com.cactus.guard.risk.repository.RiskHistoryRepository;
import br.com.cactus.guard.screening.repository.ScreeningResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consolida o dossie do case. Nao e readOnly: o acesso e registrado em data_access_log (LGPD).
 * Listas limitadas para o dossie nao crescer sem limite.
 */
@Service
@RequiredArgsConstructor
public class CaseDossierService {

    static final int MAX_PERIOD_TRANSACTIONS = 200;
    static final int MAX_RISK_HISTORY = 20;
    static final int MAX_AUDIT = 200;
    /** Sem periodo definido no case: janela de 90 dias anteriores a ocorrencia ate a selecao. */
    static final Duration DEFAULT_LOOKBACK = Duration.ofDays(90);

    private final CaseRepository caseRepository;
    private final CaseService caseService;
    private final CaseNoteService noteService;
    private final CaseEvidenceService evidenceService;
    private final AlertTransactionRepository alertTransactionRepository;
    private final CaseDossierQueryRepository queryRepository;
    private final RiskHistoryRepository riskHistoryRepository;
    private final BeneficialOwnerRepository beneficialOwnerRepository;
    private final ScreeningResultRepository screeningResultRepository;
    private final AuditLogRepository auditLogRepository;
    private final DataAccessService dataAccessService;

    @Transactional
    public CaseDossierResponse dossier(UUID caseId) {
        Case c = caseRepository.findById(caseId).orElseThrow(() -> new BusinessException("Case not found"));
        dataAccessService.log("CASE", caseId,
                "dossier:party(masked),risk,pep,ubo(masked),transactions,screening,evidences,notes,audit",
                "CONSULTA_DOSSIE", null);

        Party party = c.getParty();
        Alert alert = c.getAlert();

        AlertInfo alertInfo = alert == null ? null : new AlertInfo(alert.getId(), alert.getType(), alert.getSeverity(),
                alert.getStatus(), alert.getTitle(), alert.getRuleCode(), alert.getRuleVersion(),
                AlertService.evidenceItems(alert.getEvidence()));

        List<TransactionSummary> alertTx = alert == null ? List.of()
                : alertTransactionRepository.findWithTransactionByAlertId(alert.getId()).stream()
                .map(at -> TransactionSummary.from(at.getTransaction())).toList();

        Instant to = c.getSelectedAt() != null ? c.getSelectedAt() : Instant.now();
        Instant from;
        if (c.getPeriodStart() != null) {
            from = c.getPeriodStart().atStartOfDay(BusinessCalendar.BRAZIL).toInstant();
            if (c.getPeriodEnd() != null) {
                to = c.getPeriodEnd().plusDays(1).atStartOfDay(BusinessCalendar.BRAZIL).toInstant();
            }
        } else {
            Instant ref = c.getOccurredAt() != null ? c.getOccurredAt() : c.getCreatedAt();
            from = ref.minus(DEFAULT_LOOKBACK);
        }
        List<TransactionSummary> periodTx = party == null ? List.of()
                : queryRepository.transactionsInWindow(party.getId(), from, to, MAX_PERIOD_TRANSACTIONS).stream()
                .map(TransactionSummary::from).toList();

        List<RiskHistoryItem> risk = party == null ? List.of()
                : riskHistoryRepository.findByPartyIdOrderByCreatedAtDesc(party.getId(), PageRequest.of(0, MAX_RISK_HISTORY))
                .map(h -> new RiskHistoryItem(h.getPreviousLevel(), h.getNewLevel(), h.getPreviousScore(),
                        h.getNewScore(), h.getReason(), h.getChangedBy() != null ? h.getChangedBy().getName() : null,
                        h.getCreatedAt())).getContent();

        List<UboItem> ubos = party == null ? List.of()
                : beneficialOwnerRepository.findByPartyId(party.getId()).stream()
                .map(b -> new UboItem(b.getId(), b.getOwnerName(), DataMasker.maskDocument(b.getOwnerDocument()),
                        b.getOwnershipPct(), b.getControlType(), b.getOwnerType(), b.isPep(),
                        b.isFinalBeneficiary(), b.isIndirect(), chain(b.getOwnershipChain()))).toList();

        List<ScreeningHitItem> hits = party == null ? List.of()
                : screeningResultRepository.findByPartyId(party.getId()).stream()
                .map(r -> new ScreeningHitItem(r.getId(), r.getListCode(), r.getListType(), r.getMatchedName(),
                        r.getMatchScore(), r.getMatchType(), r.getStatus(), r.getReviewJustification(),
                        r.getReviewedAt(), r.getCreatedAt())).toList();

        List<AuditItem> audit = auditLogRepository.findByEntityTypeAndEntityId("CASE", caseId,
                        PageRequest.of(0, MAX_AUDIT, Sort.by("createdAt").ascending())).stream()
                .map(a -> new AuditItem(a.getAction(), a.getUser() != null ? a.getUser().getId() : null,
                        a.getUser() != null ? a.getUser().getName() : null, a.getNewValue(), a.getCreatedAt()))
                .toList();

        return new CaseDossierResponse(caseService.toResponse(c), alertInfo,
                party != null ? PartySummary.from(party) : null, risk, party != null ? pep(party) : null, ubos,
                alertTx, periodTx, new PeriodWindow(from, to), hits,
                evidenceService.list(caseId), noteService.list(caseId), audit);
    }

    private static PepInfo pep(Party p) {
        return new PepInfo(p.isPep(), p.getPepStatus(), p.getPepCategory(), p.getPepRole(), p.getPepStartDate(),
                p.getPepEndDate(), p.getPepExpiryDate(),
                p.getPepApprovalBy() != null ? p.getPepApprovalBy().getId() : null,
                p.getPepApprovalBy() != null ? p.getPepApprovalBy().getName() : null, p.getPepApprovalAt());
    }

    /** Cadeia societaria sem hashes de documento: apenas nome e percentual. */
    private static List<Map<String, Object>> chain(List<Map<String, Object>> raw) {
        if (raw == null) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : raw) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("name", m.get("name"));
            n.put("pct", m.get("pct"));
            out.add(n);
        }
        return out;
    }
}
