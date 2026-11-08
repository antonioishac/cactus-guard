package br.com.cactus.guard.party;

import br.com.cactus.guard.party.dto.PartyRequest;
import br.com.cactus.guard.party.dto.PartyResponse;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.party.service.BeneficialOwnerService;
import br.com.cactus.guard.party.service.PartyService;
import br.com.cactus.guard.support.AbstractPostgresIT;
import br.com.cactus.guard.transaction.dto.TransactionRequest;
import br.com.cactus.guard.transaction.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Contra PostgreSQL real com Flyway (valida a V30): filtros combinados com full-text, PUT sem corromper o CPF,
 * motivo longo de revelacao (access_reason alargado), PII cifrada de UBO/contraparte (coluna de 512) e auditoria jsonb.
 */
@AutoConfigureMockMvc
class PartyContractsIT extends AbstractPostgresIT {

    @Autowired private MockMvc mvc;
    @Autowired private PartyService partyService;
    @Autowired private PartyRepository partyRepository;
    @Autowired private BeneficialOwnerService boService;
    @Autowired private TransactionService transactionService;
    @Autowired private JdbcTemplate jdbc;

    private static String cpf() {
        return String.format("%011d", ThreadLocalRandom.current().nextLong(10_000_000_000L, 99_999_999_999L));
    }

    private PartyResponse create(String doc, String type, String name) {
        PartyRequest r = new PartyRequest();
        r.setType(type);
        r.setDocument(doc);
        r.setName(name);
        r.setEmail("x@email.com");
        return partyService.create(r);
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void fullTextCombinedWithFiltersAndDocumentLikeQuery() throws Exception {
        String tag = "Zebrafish" + UUID.randomUUID().toString().substring(0, 8).replaceAll("[^a-z]", "x");
        String doc = cpf();
        create(doc, "PF", tag + " Silva");
        create(String.format("%014d", ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L)),
                "PJ", tag + " Comercio");

        mvc.perform(get("/api/v1/parties").param("q", tag).param("sort", "createdAt,desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/parties").param("q", tag).param("type", "PJ"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("PJ"));
        mvc.perform(get("/api/v1/parties").param("q", tag).param("type", "PF").param("pep", "true"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/parties").param("q", "inexistente" + tag))
                .andExpect(jsonPath("$.totalElements").value(0));
        // q com cara de CPF casa por HMAC (o documento esta cifrado, nao esta no tsvector)
        String formatted = doc.substring(0, 3) + "." + doc.substring(3, 6) + "." + doc.substring(6, 9) + "-" + doc.substring(9);
        mvc.perform(get("/api/v1/parties").param("q", formatted))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/parties").param("document", doc))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void putWithMaskedValuesKeepsCpfOnRealSchema() throws Exception {
        String doc = cpf();
        PartyResponse created = create(doc, "PF", "Edicao Real");
        mvc.perform(put("/api/v1/parties/" + created.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PF\",\"name\":\"Edicao Real 2\",\"document\":\"" + created.getDocument()
                                + "\",\"email\":\"" + created.getEmail() + "\"}"))
                .andExpect(status().isOk());
        Party p = partyRepository.findById(created.getId()).orElseThrow();
        assertEquals(doc, p.getDocument());
        assertEquals("x@email.com", p.getEmail());
        assertTrue(partyRepository.findByDocumentHash(br.com.cactus.guard.common.util.DocumentHash.of(doc)).isPresent());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void revealWithLongReasonIsPersisted() throws Exception {
        PartyResponse created = create(cpf(), "PF", "Revelacao Real");
        String reason = "Conferencia documental solicitada pelo supervisor no caso 4711 - ".repeat(4).substring(0, 250);
        mvc.perform(post("/api/v1/parties/" + created.getId() + "/reveal").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"field\":\"email\",\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("x@email.com"));
        Integer len = jdbc.queryForObject("SELECT max(length(access_reason)) FROM data_access_log WHERE entity_id = ?",
                Integer.class, created.getId());
        assertTrue(len != null && len > 100, "access_reason deve comportar motivos > 100 caracteres (V30)");
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void uboAndCounterpartyDocumentsAreEncryptedOnRealSchema() {
        PartyResponse company = create(String.format("%014d", ThreadLocalRandom.current().nextLong(10_000_000_000_000L, 99_999_999_999_999L)),
                "PJ", "Empresa Real");
        String ownerDoc = cpf();
        var owner = boService.add(company.getId(), ownerDoc, "Socio Real", new BigDecimal("100"), "DIRECT", false);
        assertTrue(jdbc.queryForObject("SELECT owner_document FROM beneficial_owners WHERE id = ?", String.class, owner.getId())
                .startsWith("v1:"));

        TransactionRequest t = new TransactionRequest();
        t.setPartyId(company.getId());
        t.setType("TED");
        t.setAmount(new BigDecimal("10"));
        t.setCounterpartyDocument("12345678000190");
        t.setDirection("OUT");
        t.setTransactionDate(Instant.now());
        var resp = transactionService.create(t);
        assertEquals("**********0190", resp.getCounterpartyDocument());
        assertTrue(jdbc.queryForObject("SELECT counterparty_document FROM transactions WHERE id = ?", String.class, resp.getId())
                .startsWith("v1:"));
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void auditSearchWorksOnJsonb() throws Exception {
        PartyResponse created = create(cpf(), "PF", "Auditada");
        mvc.perform(get("/api/v1/audit").param("entityId", created.getId().toString()).param("action", "PARTY_CREATED")
                        .param("from", "2000-01-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
