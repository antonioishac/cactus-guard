package br.com.cactus.guard.transaction;

import br.com.cactus.guard.admin.service.DocumentRehashService;
import br.com.cactus.guard.common.util.DocumentHash;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Resposta de transacao (direction, cash, paymentMethod, contraparte) e PII da contraparte cifrada. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionContractTest {

    @Autowired private MockMvc mvc;
    @Autowired private PartyRepository partyRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DocumentRehashService rehashService;

    private Party party() {
        String doc = String.format("%011d", ThreadLocalRandom.current().nextLong(10_000_000_000L, 99_999_999_999L));
        return partyRepository.save(Party.builder().type("PF").document(doc).documentHash(DocumentHash.of(doc))
                .name("Titular").status("ACTIVE").riskLevel("LOW").build());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void responseExposesPldFieldsAndMasksCounterpartyPii() throws Exception {
        Party p = party();
        String cdoc = "12345678000190";
        String body = """
                {"partyId":"%s","type":"PIX","amount":1500.00,"currency":"BRL","direction":"OUT","paymentMethod":"PIX",
                 "counterpartyName":"Fulano","counterpartyDocument":"%s","counterpartyCountry":"BR",
                 "counterpartyBankIspb":"00000000","counterpartyBranch":"0001","counterpartyAccount":"1234567",
                 "endToEndId":"E00000000202611081200abcdef12345","transactionDate":"%s"}
                """.formatted(p.getId(), cdoc, Instant.now());
        mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("OUT"))
                .andExpect(jsonPath("$.cash").value(false))
                .andExpect(jsonPath("$.isCash").value(false))
                .andExpect(jsonPath("$.paymentMethod").value("PIX"))
                .andExpect(jsonPath("$.counterpartyCountry").value("BR"))
                .andExpect(jsonPath("$.counterpartyBankIspb").value("00000000"))
                .andExpect(jsonPath("$.counterpartyBranch").value("0001"))
                .andExpect(jsonPath("$.counterpartyAccount").value("***" + "4567"))
                .andExpect(jsonPath("$.counterpartyDocument").value("**********0190"))
                .andExpect(jsonPath("$.endToEndId").value("E00000000202611081200abcdef12345"))
                .andExpect(jsonPath("$.amountBrl").value(1500.0));
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void counterpartyDocumentIsEncryptedAtRestWithHash() throws Exception {
        Party p = party();
        String cdoc = "98765432000155";
        String body = """
                {"partyId":"%s","type":"TED","amount":10.00,"direction":"IN","counterpartyDocument":"%s","transactionDate":"%s"}
                """.formatted(p.getId(), cdoc, Instant.now());
        mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        UUID id = transactionRepository.findByPartyId(p.getId(), org.springframework.data.domain.Pageable.unpaged())
                .getContent().get(0).getId();

        String raw = jdbc.queryForObject("SELECT counterparty_document FROM transactions WHERE id = ?", String.class, id);
        assertTrue(raw.startsWith("v1:"), "counterparty_document deve estar cifrado");
        assertEquals(DocumentHash.of(cdoc), jdbc.queryForObject(
                "SELECT counterparty_document_hash FROM transactions WHERE id = ?", String.class, id));
        assertEquals(cdoc, transactionRepository.findById(id).orElseThrow().getCounterpartyDocument());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void cashPaymentMethodSetsIsCash() throws Exception {
        Party p = party();
        String body = """
                {"partyId":"%s","type":"DEPOSIT","amount":100.00,"direction":"IN","paymentMethod":"CASH","transactionDate":"%s"}
                """.formatted(p.getId(), Instant.now());
        mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cash").value(true))
                .andExpect(jsonPath("$.isCash").value(true))
                .andExpect(jsonPath("$.direction").value("IN"));
    }

    @Test
    void legacyPlaintextCounterpartyDocumentIsEncryptedByRehash() {
        Party p = party();
        UUID id = transactionRepository.save(br.com.cactus.guard.transaction.entity.Transaction.builder()
                .party(p).type("TED").amount(new java.math.BigDecimal("5")).transactionDate(Instant.now()).build()).getId();
        // simula dado anterior a V30 (em claro, sem hash)
        jdbc.update("UPDATE transactions SET counterparty_document = ?, counterparty_document_hash = NULL WHERE id = ?",
                "11122233344", id);

        rehashService.encryptLegacyPlaintext();

        String raw = jdbc.queryForObject("SELECT counterparty_document FROM transactions WHERE id = ?", String.class, id);
        assertTrue(raw.startsWith("v1:"));
        assertEquals(DocumentHash.of("11122233344"), jdbc.queryForObject(
                "SELECT counterparty_document_hash FROM transactions WHERE id = ?", String.class, id));
        assertEquals("11122233344", transactionRepository.findById(id).orElseThrow().getCounterpartyDocument());
        // idempotente
        assertEquals(0, rehashService.encryptLegacyPlaintext());
    }
}
