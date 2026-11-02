package br.com.cactus.guard.kyc;

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
class KycTypeTest {

    @Autowired
    private KycService kycService;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void onboardingTypeShouldBeOnboarding() {
        Party party = Party.builder()
                .type("PF")
                .document("58585858585")
                .name("Type Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        var process = kycService.startOnboarding(party.getId());
        assertEquals("ONBOARDING", process.getType());
    }
}
