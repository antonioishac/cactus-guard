package br.com.cactus.guard.screening.service;

import br.com.cactus.guard.audit.service.DataAccessService;

import br.com.cactus.guard.alert.entity.Alert;
import br.com.cactus.guard.alert.repository.AlertRepository;
import br.com.cactus.guard.integration.screening.ScreeningOrchestrator;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScreeningService {

    private final ScreeningResultRepository resultRepository;
    private final ScreeningOrchestrator orchestrator;
    private final PartyRepository partyRepository;
    private final AlertRepository alertRepository;
    private final DataAccessService dataAccessService;
    private final ScreeningListRepository listRepository;

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
        return toResponse(result);
    }

    @Transactional(readOnly = true)
    public List<ScreeningResultResponse> findByParty(UUID partyId) {
        dataAccessService.log("SCREENING", partyId, "listCode,matchScore", "CONSULTA", null);
        return resultRepository.findByPartyId(partyId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Listas restritivas (PEP, sanções) ativas para screening. */
    @Transactional(readOnly = true)
    public List<ScreeningList> findAllLists() {
        return listRepository.findByActiveTrueOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public Page<ScreeningResultResponse> findByStatus(String status, Pageable pageable) {
        return resultRepository.findByStatus(status, pageable).map(this::toResponse);
    }

    @Transactional
    public ScreeningResultResponse review(UUID resultId, String newStatus) {
        ScreeningResult result = resultRepository.findById(resultId)
                .orElseThrow(() -> new br.com.cactus.guard.common.exception.BusinessException("Screening result not found"));
        result.setStatus(newStatus);
        result = resultRepository.save(result);

        if ("CONFIRMED".equals(newStatus) && result.getParty() != null) {
            Alert alert = Alert.builder()
                    .party(result.getParty())
                    .type("SCREENING")
                    .severity("HIGH")
                    .status("OPEN")
                    .title("Match confirmado em screening")
                    .description("Parte " + result.getParty().getName() + " confirmada em lista " + result.getListCode())
                    .ruleCode("SCREENING_CONFIRMED")
                    .createdAt(Instant.now())
                    .build();
            alertRepository.save(alert);
        }

        return toResponse(result);
    }

    private ScreeningResultResponse toResponse(ScreeningResult r) {
        return ScreeningResultResponse.builder()
                .id(r.getId())
                .partyId(r.getParty() != null ? r.getParty().getId() : null)
                .listCode(r.getListCode())
                .matchedName(r.getEntry() != null ? r.getEntry().getFullName() : null)
                .matchedDocument(r.getEntry() != null ? r.getEntry().getDocument() : null)
                .matchScore(r.getMatchScore())
                .matchType(r.getMatchType())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
