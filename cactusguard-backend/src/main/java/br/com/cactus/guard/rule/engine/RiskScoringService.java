package br.com.cactus.guard.rule.engine;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import br.com.cactus.guard.screening.entity.ScreeningResult;
import br.com.cactus.guard.screening.repository.ScreeningResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskScoringService {

    private final PartyRepository partyRepository;
    private final ScreeningResultRepository screeningResultRepository;
    private final RiskHistoryService riskHistoryService;

    @Transactional
    public void recalculateAllRiskScores() {
        List<Party> parties = partyRepository.findAll();
        for (Party party : parties) {
            String previousLevel = party.getRiskLevel();
            String newLevel = calculateRisk(party);
            
            if (!newLevel.equals(previousLevel)) {
                riskHistoryService.record(party, null, null, previousLevel, newLevel, 
                        "Recálculo automático", null);
            }
            
            party.setRiskLevel(newLevel);
            partyRepository.save(party);
        }
        log.info("Risk scores recalculated for {} parties", parties.size());
    }

    public String calculateRisk(Party party) {
        List<ScreeningResult> results = screeningResultRepository.findByPartyId(party.getId());

        boolean hasCriticalMatch = results.stream()
                .anyMatch(r -> "CONFIRMED".equals(r.getStatus()) && 
                        (r.getListCode() != null && (r.getListCode().contains("SANCTION") || r.getListCode().contains("OFAC"))));

        if (hasCriticalMatch) return "CRITICAL";

        boolean hasPepMatch = results.stream()
                .anyMatch(r -> "CONFIRMED".equals(r.getStatus()) && 
                        r.getListCode() != null && r.getListCode().contains("PEP"));

        if (hasPepMatch) return "HIGH";

        boolean hasPending = results.stream()
                .anyMatch(r -> "PENDING".equals(r.getStatus()));

        if (hasPending) return "MEDIUM";

        return "LOW";
    }
}
