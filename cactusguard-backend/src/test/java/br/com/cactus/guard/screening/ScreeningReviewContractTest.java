package br.com.cactus.guard.screening;

import br.com.cactus.guard.common.util.DocumentHash;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.screening.entity.ScreeningResult;
import br.com.cactus.guard.screening.repository.ScreeningResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Revisao de hit por JSON (justificativa obrigatoria), compatibilidade com form, resposta enriquecida e catalogo. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScreeningReviewContractTest {

    private static final String JUST = "Homonimia descartada: data de nascimento e filiacao divergem";

    @Autowired private MockMvc mvc;
    @Autowired private PartyRepository partyRepository;
    @Autowired private ScreeningResultRepository resultRepository;

    private ScreeningResult hit(String listCode) {
        String doc = String.format("%011d", ThreadLocalRandom.current().nextLong(10_000_000_000L, 99_999_999_999L));
        Party p = partyRepository.save(Party.builder().type("PF").document(doc).documentHash(DocumentHash.of(doc))
                .name("Parte Hit").status("ACTIVE").riskLevel("LOW").build());
        return resultRepository.save(ScreeningResult.builder().party(p).listCode(listCode).matchedName("Parte Hit Homonimo")
                .matchScore(new BigDecimal("91.0")).status("PENDING").build());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void reviewJsonRecordsJustificationAndReturnsEnrichedResponse() throws Exception {
        ScreeningResult r = hit("PEP_BR");
        mvc.perform(post("/api/v1/screening/" + r.getId() + "/review").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FALSE_POSITIVE\",\"justification\":\"" + JUST + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FALSE_POSITIVE"))
                .andExpect(jsonPath("$.partyName").value("Parte Hit"))
                .andExpect(jsonPath("$.reviewJustification").value(JUST))
                .andExpect(jsonPath("$.reviewedAt").exists())
                .andExpect(jsonPath("$.requiresAssetFreeze").value(false))
                .andExpect(jsonPath("$.matchedName").value("Parte Hit Homonimo"));
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void shortOrMissingJustificationIsRejected() throws Exception {
        ScreeningResult r = hit("PEP_BR");
        String url = "/api/v1/screening/" + r.getId() + "/review";
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FALSE_POSITIVE\",\"justification\":\"curta\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISMISSED\",\"justification\":\"" + JUST + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void legacyFormEncodedReviewStillWorks() throws Exception {
        ScreeningResult r = hit("PEP_BR");
        mvc.perform(post("/api/v1/screening/" + r.getId() + "/review").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("status", "FALSE_POSITIVE").param("justification", JUST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewJustification").value(JUST));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void csnuHitFlagsAssetFreezeRequirement() throws Exception {
        ScreeningResult r = hit("CSNU");
        mvc.perform(get("/api/v1/screening/party/" + r.getParty().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requiresAssetFreeze").value(true))
                .andExpect(jsonPath("$[0].partyName").value("Parte Hit"));
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void listsCatalogReturnsRealLists() throws Exception {
        mvc.perform(get("/api/v1/screening/lists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='CSNU')].requiresAssetFreeze").value(true))
                .andExpect(jsonPath("$[0].code").exists())
                .andExpect(jsonPath("$[0].active").exists());
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void auditorCannotReview() throws Exception {
        ScreeningResult r = hit("PEP_BR");
        mvc.perform(post("/api/v1/screening/" + r.getId() + "/review").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FALSE_POSITIVE\",\"justification\":\"" + JUST + "\"}"))
                .andExpect(status().isForbidden());
    }
}
