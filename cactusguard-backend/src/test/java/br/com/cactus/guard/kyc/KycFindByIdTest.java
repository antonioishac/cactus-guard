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
class KycFindByIdTest {

    @Autowired
    private KycService kycService;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void findByIdShouldReturnProcess() {
        Party party = Party.builder()
                .type("PF")
                .document("49494949494")
                .name("Find By Id KYC")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        var process = kycService.startOnboarding(party.getId());
        var found = kycService.findById(process.getId());

        assertEquals(process.getId(), found.getId());
        assertEquals("IN_PROGRESS", found.getStatus());
    }
}
