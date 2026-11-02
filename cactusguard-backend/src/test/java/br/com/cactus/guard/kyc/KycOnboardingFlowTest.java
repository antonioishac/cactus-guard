package br.com.cactus.guard.kyc;

import br.com.cactus.guard.kyc.dto.KycProcessResponse;
import br.com.cactus.guard.kyc.service.KycService;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class KycOnboardingFlowTest {

    @Autowired
    private KycService kycService;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void fullOnboardingFlowShouldWork() {
        Party party = Party.builder()
                .type("PF")
                .document("99988877766")
                .name("Onboarding Flow Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        KycProcessResponse started = kycService.startOnboarding(party.getId());
        assertEquals("IN_PROGRESS", started.getStatus());
        assertEquals("ONBOARDING", started.getType());
        assertNotNull(started.getExpiresAt());

        KycProcessResponse approved = kycService.approve(started.getId());
        assertEquals("APPROVED", approved.getStatus());
        assertNotNull(approved.getCompletedAt());
    }
}
