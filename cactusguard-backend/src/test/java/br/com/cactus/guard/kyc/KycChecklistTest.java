package br.com.cactus.guard.kyc;

import br.com.cactus.guard.kyc.service.KycChecklistService;
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
class KycChecklistTest {

    @Autowired
    private KycChecklistService checklistService;

    @Autowired
    private KycService kycService;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void addAndCompleteChecklistItemShouldWork() {
        Party party = Party.builder()
                .type("PF")
                .document("22200033344")
                .name("Checklist Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        var process = kycService.startOnboarding(party.getId());
        var item = checklistService.addItem(process.getId(), "DOC_RG", "Documento de identidade", true);

        assertFalse(item.isCompleted());

        var completed = checklistService.complete(item.getId());
        assertTrue(completed.isCompleted());
        assertNotNull(completed.getCompletedAt());
    }
}
