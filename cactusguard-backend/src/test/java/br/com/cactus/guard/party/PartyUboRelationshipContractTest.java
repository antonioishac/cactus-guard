package br.com.cactus.guard.party;

import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.party.service.BeneficialOwnerService;
import br.com.cactus.guard.party.service.PartyRelationshipService;
import br.com.cactus.guard.common.util.DocumentHash;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** UBO e relacionamentos: DTO (nunca entity), documento cifrado em repouso + hash, relatedParty resumido/mascarado. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartyUboRelationshipContractTest {

    @Autowired private MockMvc mvc;
    @Autowired private PartyRepository partyRepository;
    @Autowired private BeneficialOwnerService boService;
    @Autowired private PartyRelationshipService relationshipService;
    @Autowired private JdbcTemplate jdbc;

    private static String cpf() {
        return String.format("%011d", ThreadLocalRandom.current().nextLong(10_000_000_000L, 99_999_999_999L));
    }

    private Party party(String doc, String type, String name) {
        return partyRepository.save(Party.builder().type(type).document(doc).documentHash(DocumentHash.of(doc))
                .name(name).email("secreto@email.com").status("ACTIVE").riskLevel("LOW").build());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void uboDocumentIsEncryptedAtRestHashedAndMaskedInResponse() throws Exception {
        Party company = party(String.format("%014d", ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L)),
                "PJ", "Empresa UBO Ltda");
        String ownerDoc = cpf();
        Party ownerParty = party(ownerDoc, "PF", "Socio Cadastrado");

        var owner = boService.add(company.getId(), ownerDoc, "Socio Cadastrado", new BigDecimal("60.00"), "DIRECT", false);

        String raw = jdbc.queryForObject("SELECT owner_document FROM beneficial_owners WHERE id = ?", String.class, owner.getId());
        assertNotEquals(ownerDoc, raw, "owner_document deve estar cifrado");
        assertTrue(raw.startsWith("v1:"));
        String hash = jdbc.queryForObject("SELECT owner_document_hash FROM beneficial_owners WHERE id = ?", String.class, owner.getId());
        assertEquals(DocumentHash.of(ownerDoc), hash);

        mvc.perform(get("/api/v1/parties/" + company.getId() + "/beneficial-owners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ownerDocument").value("*******" + ownerDoc.substring(7)))
                .andExpect(jsonPath("$[0].ownerName").value("Socio Cadastrado"))
                .andExpect(jsonPath("$[0].ownerPartyId").value(ownerParty.getId().toString()))
                .andExpect(jsonPath("$[0].ownerParty.name").value("Socio Cadastrado"))
                .andExpect(jsonPath("$[0].ownerType").value("PF"))
                .andExpect(jsonPath("$[0].finalBeneficiary").value(true))
                .andExpect(jsonPath("$[0].isFinalBeneficiary").value(true))
                .andExpect(jsonPath("$[0].indirect").value(false))
                .andExpect(jsonPath("$[0].isIndirect").value(false))
                .andExpect(jsonPath("$[0].party").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void relationshipExposesOnlyMaskedRelatedPartySummary() throws Exception {
        Party a = party(cpf(), "PF", "Parte A");
        String relatedDoc = cpf();
        Party b = party(relatedDoc, "PF", "Parte B");
        relationshipService.add(a.getId(), b.getId(), "SPOUSE", "Conjuge");

        mvc.perform(get("/api/v1/parties/" + a.getId() + "/relationships"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].relationshipType").value("SPOUSE"))
                .andExpect(jsonPath("$[0].relatedParty.id").value(b.getId().toString()))
                .andExpect(jsonPath("$[0].relatedParty.name").value("Parte B"))
                .andExpect(jsonPath("$[0].relatedParty.document").value("*******" + relatedDoc.substring(7)))
                .andExpect(jsonPath("$[0].relatedParty.email").doesNotExist())
                .andExpect(jsonPath("$[0].party").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void unknownRelatedPartyKeepsRandomIdWorking() throws Exception {
        mvc.perform(get("/api/v1/parties/" + UUID.randomUUID() + "/beneficial-owners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
