package br.com.cactus.guard.rule.engine;

import br.com.cactus.guard.risk.service.RiskHistoryService;

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
            
            java.math.BigDecimal previousScore = party.getRiskScore();
            java.math.BigDecimal newScore = scoreForLevel(newLevel);

            if (!newLevel.equals(previousLevel)) {
                // risk_history.new_score e NOT NULL (V15): registra o score representativo do nivel
                riskHistoryService.record(party, previousScore, newScore, previousLevel, newLevel,
                        "Recálculo automático", null);
            }

            party.setRiskLevel(newLevel);
            party.setRiskScore(newScore);
            partyRepository.save(party);
        }
        log.info("Risk scores recalculated for {} parties", parties.size());
    }

    /** Score numerico (0-100) representativo de cada nivel de risco calculado. */
    static java.math.BigDecimal scoreForLevel(String level) {
        return switch (level) {
            case "CRITICAL" -> java.math.BigDecimal.valueOf(100);
            case "HIGH" -> java.math.BigDecimal.valueOf(75);
            case "MEDIUM" -> java.math.BigDecimal.valueOf(50);
            default -> java.math.BigDecimal.valueOf(25);
        };
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
