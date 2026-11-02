package br.com.cactus.guard.kyc;

import br.com.cactus.guard.kyc.repository.KycProcessRepository;
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
class KycFindByPartyTest {

    @Autowired
    private KycService kycService;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private KycProcessRepository kycRepository;

    @Test
    void findByPartyShouldReturnProcesses() {
        Party party = Party.builder()
                .type("PF")
                .document("29292929292")
                .name("Find By Party")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        kycService.startOnboarding(party.getId());

        var processes = kycRepository.findByPartyId(party.getId(), org.springframework.data.domain.Pageable.unpaged());
        assertFalse(processes.isEmpty());
    }
}
