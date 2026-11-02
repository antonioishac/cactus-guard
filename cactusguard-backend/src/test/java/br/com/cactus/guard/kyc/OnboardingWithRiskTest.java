package br.com.cactus.guard.kyc;

import br.com.cactus.guard.kyc.service.KycService;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.rule.engine.RiskScoringService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class OnboardingWithRiskTest {

    @Autowired
    private KycService kycService;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private RiskScoringService riskScoringService;

    @Test
    void onboardingApprovalShouldAllowRiskCalculation() {
        Party party = Party.builder()
                .type("PF")
                .document("50505050505")
                .name("Onboarding Risk Test")
                .status("ACTIVE")
                .riskLevel("PENDING")
                .build();
        party = partyRepository.save(party);

        var process = kycService.startOnboarding(party.getId());
        kycService.approve(process.getId());

        String risk = riskScoringService.calculateRisk(party);
        assertNotNull(risk);
    }
}
