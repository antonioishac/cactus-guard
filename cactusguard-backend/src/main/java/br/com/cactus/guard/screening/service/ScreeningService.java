package br.com.cactus.guard.screening.service;

import br.com.cactus.guard.event.dto.DomainEventType;
import br.com.cactus.guard.event.service.DomainEventPublisher;
import br.com.cactus.guard.event.service.EventPayloads;
import br.com.cactus.guard.common.audit.AuditPayload;
import br.com.cactus.guard.audit.service.DataAccessService;

import br.com.cactus.guard.alert.service.AlertCreationService;
import br.com.cactus.guard.alert.service.AlertCreationService.AlertDraft;
import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.common.security.CurrentUserService;
import br.com.cactus.guard.rule.engine.RiskScoringService;
import br.com.cactus.guard.integration.screening.ScreeningOrchestrator;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.screening.dto.ScreeningListResponse;
import br.com.cactus.guard.screening.dto.ScreeningResultResponse;
import br.com.cactus.guard.screening.entity.ScreeningResult;
import br.com.cactus.guard.screening.entity.ScreeningList;
import br.com.cactus.guard.screening.repository.ScreeningListRepository;
import br.com.cactus.guard.screening.repository.ScreeningResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScreeningService {

    private final ScreeningResultRepository resultRepository;
    private final ScreeningOrchestrator orchestrator;
    private final PartyRepository partyRepository;
    private final DataAccessService dataAccessService;
    private final ScreeningListRepository listRepository;
    private final AlertCreationService alertCreation;
    private final AuditService auditService;
    private final CurrentUserService currentUser;
    private final RiskScoringService riskScoring;
    private final DomainEventPublisher events;

    @Async
    @Transactional
    public CompletableFuture<ScreeningResultResponse> screenAsync(UUID partyId, List<String> listCodes) {
        log.info("Async screening started for party: {}", partyId);
        ScreeningResultResponse result = screen(partyId, listCodes);
        return CompletableFuture.completedFuture(result);
    }

    @Transactional
    public ScreeningResultResponse screen(UUID partyId, List<String> listCodes) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new br.com.cactus.guard.common.exception.BusinessException("Parte não encontrada"));
        ScreeningResult result = orchestrator.screenParty(party, listCodes);
        return toResponse(result, freezeCodes());
    }

    @Transactional(readOnly = true)
    public List<ScreeningResultResponse> findByParty(UUID partyId) {
        dataAccessService.log("SCREENING", partyId, "listCode,matchScore", "CONSULTA", null);
        Set<String> freeze = freezeCodes();
        return resultRepository.findByPartyId(partyId).stream()
                .map(r -> toResponse(r, freeze))
                .toList();
    }

    /** Catalogo completo (ativas e inativas) para GET /screening/lists. */
    @Transactional(readOnly = true)
    public List<ScreeningListResponse> catalog() {
        return listRepository.findAll(org.springframework.data.domain.Sort.by("code")).stream()
                .map(ScreeningListResponse::from).toList();
    }

    /** Listas restritivas (PEP, sanções) ativas para screening. */
    @Transactional(readOnly = true)
    public List<ScreeningList> findAllLists() {
        return listRepository.findByActiveTrueOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public Page<ScreeningResultResponse> findByStatus(String status, Pageable pageable) {
        Set<String> freeze = freezeCodes();
        return resultRepository.findByStatus(status, pageable).map(r -> toResponse(r, freeze));
    }

    @Transactional
    public ScreeningResultResponse review(UUID resultId, String newStatus) {
        return review(resultId, newStatus, null);
    }

    /**
     * Revisao humana do hit. CONFIRMED gera alerta; em lista com {@code requiresAssetFreeze}
     * (CSNU - Lei 13.810/2019, Res. BCB 44/2020) a indisponibilidade de ativos e ativada
     * imediatamente, o alerta e CRITICAL e o risco da parte vira CRITICAL.
     */
    @Transactional
    public ScreeningResultResponse review(UUID resultId, String newStatus, String justification) {
        ScreeningResult result = resultRepository.findById(resultId)
                .orElseThrow(() -> new br.com.cactus.guard.common.exception.BusinessException("Screening result not found"));
        String oldStatus = result.getStatus();
        result.setStatus(newStatus);
        result.setReviewedAt(Instant.now());
        result.setReviewedBy(currentUser.user().orElse(null));
        if (justification != null && !justification.isBlank()) {
            result.setReviewJustification(justification.trim());
        }
        result = resultRepository.save(result);
        Map<String, Object> reviewAudit = new LinkedHashMap<>();
        reviewAudit.put("status", newStatus);
        reviewAudit.put("listCode", result.getListCode());
        reviewAudit.put("justification", result.getReviewJustification());
        AuditPayload.log(auditService, "SCREENING_REVIEWED", "SCREENING_RESULT", result.getId(),
                Map.of("status", String.valueOf(oldStatus)), reviewAudit);

        if ("CONFIRMED".equals(newStatus) && !"CONFIRMED".equals(oldStatus) && result.getParty() != null) {
            onConfirmed(result);
        }
        return toResponse(result, freezeCodes());
    }

    private void onConfirmed(ScreeningResult result) {
        Party party = result.getParty();
        ScreeningList list = result.getListCode() == null ? null
                : listRepository.findByCode(result.getListCode()).orElse(null);
        boolean freeze = list != null && list.isRequiresAssetFreeze();

        if (freeze && !party.isAssetFreezeActive()) {
            party.setAssetFreezeActive(true);
            party.setAssetFreezeAt(Instant.now());
            party.setAssetFreezeReference(result.getListCode() + ":" + result.getId());
            partyRepository.save(party);
            Map<String, Object> audit = new LinkedHashMap<>();
            audit.put("assetFreezeActive", true);
            audit.put("reference", party.getAssetFreezeReference());
            audit.put("basis", "Lei 13.810/2019 / Res. BCB 44/2020 - hit CONFIRMED em lista CSNU");
            AuditPayload.log(auditService, "ASSET_FREEZE_ACTIVATED", "PARTY", party.getId(),
                    Map.of("assetFreezeActive", false), audit);
            events.publish(DomainEventType.ASSET_FREEZE_ACTIVATED, party.getId(), EventPayloads.assetFreezeActivated(
                    party.getId(), party.getAssetFreezeReference(), result.getId(), result.getListCode()));
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("screeningResultId", result.getId().toString());
        evidence.put("listCode", result.getListCode());
        evidence.put("matchScore", result.getMatchScore());
        evidence.put("assetFreeze", freeze);
        alertCreation.createIfAbsent(new AlertDraft(party, null, "SCREENING", freeze ? "CRITICAL" : "HIGH",
                freeze ? "Sanção CSNU confirmada - indisponibilidade de ativos" : "Match confirmado em screening",
                "Parte " + party.getName() + " confirmada em lista " + result.getListCode(),
                "SCREENING_CONFIRMED", "SCREENING_CONFIRMED:" + result.getId(),
                null, null, evidence, List.of()));

        riskScoring.assessAndApply(party, "Screening confirmado em " + result.getListCode());
    }

    /** Codigos de lista que exigem indisponibilidade de ativos (tabela pequena; 1 consulta por chamada). */
    private Set<String> freezeCodes() {
        Set<String> codes = new HashSet<>();
        listRepository.findAll().stream().filter(ScreeningList::isRequiresAssetFreeze)
                .forEach(l -> codes.add(l.getCode()));
        return codes;
    }

    private ScreeningResultResponse toResponse(ScreeningResult r, Set<String> freezeCodes) {
        return ScreeningResultResponse.builder()
                .id(r.getId())
                .partyId(r.getParty() != null ? r.getParty().getId() : null)
                .listCode(r.getListCode())
                .matchedName(r.getEntry() != null ? r.getEntry().getFullName() : r.getMatchedName())
                .partyName(r.getParty() != null ? r.getParty().getName() : null)
                .requiresAssetFreeze(r.getListCode() != null && freezeCodes.contains(r.getListCode()))
                .reviewJustification(r.getReviewJustification())
                .reviewedAt(r.getReviewedAt())
                .reviewedByName(r.getReviewedBy() != null ? r.getReviewedBy().getName() : null)
                .matchedDocument(r.getEntry() != null ? r.getEntry().getDocument() : null)
                .matchScore(r.getMatchScore())
                .matchType(r.getMatchType())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
