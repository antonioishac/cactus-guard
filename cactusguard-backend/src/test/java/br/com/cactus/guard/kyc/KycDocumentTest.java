package br.com.cactus.guard.kyc;

import br.com.cactus.guard.kyc.service.KycDocumentService;
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
class KycDocumentTest {

    @Autowired
    private KycDocumentService documentService;

    @Autowired
    private KycService kycService;

    @Autowired
    private PartyRepository partyRepository;

    @Test
    void addAndApproveDocumentShouldWork() {
        Party party = Party.builder()
                .type("PF")
                .document("11100022233")
                .name("Doc Test")
                .status("ACTIVE")
                .build();
        party = partyRepository.save(party);

        var process = kycService.startOnboarding(party.getId());
        var doc = documentService.addDocument(process.getId(), "RG", "rg.pdf", "/uploads/rg.pdf");

        assertEquals("PENDING", doc.getStatus());

        var approved = documentService.approve(doc.getId());
        assertEquals("APPROVED", approved.getStatus());
    }
}
