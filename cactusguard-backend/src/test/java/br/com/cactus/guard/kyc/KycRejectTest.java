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
class KycRejectTest {

    @Autowired
    private KycService kycService;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void rejectOnboardingShouldUpdateStatus() {
        Party party = Party.builder()
                .type("PF")
                .document("12121212121")
                .name("Reject Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        var process = kycService.startOnboarding(party.getId());
        var rejected = kycService.reject(process.getId());

        assertEquals("REJECTED", rejected.getStatus());
        assertNotNull(rejected.getCompletedAt());
    }
}
