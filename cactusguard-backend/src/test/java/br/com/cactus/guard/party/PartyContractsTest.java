package br.com.cactus.guard.party;

import br.com.cactus.guard.audit.repository.DataAccessLogRepository;
import br.com.cactus.guard.party.dto.PartyRequest;
import br.com.cactus.guard.party.dto.PartyResponse;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.party.service.PartyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Contratos de cadastro/edicao/filtros/revelacao de PII/erro global (alinhamento backend x front). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartyContractsTest {

    @Autowired private MockMvc mvc;
    @Autowired private PartyService partyService;
    @Autowired private PartyRepository partyRepository;
    @Autowired private DataAccessLogRepository dataAccessLogRepository;

    private static String randomCpf() {
        return String.format("%011d", ThreadLocalRandom.current().nextLong(10_000_000_000L, 99_999_999_999L));
    }

    private PartyResponse create(String doc) {
        PartyRequest r = new PartyRequest();
        r.setType("PF");
        r.setDocument(doc);
        r.setName("Maria Souza");
        r.setEmail("maria.souza@email.com");
        r.setPhone("11987654321");
        r.setMotherName("Ana Paula Souza");
        r.setDeclaredMonthlyIncome(new BigDecimal("5000.00"));
        return partyService.create(r);
    }

    // ------------------------------------------------------------ PUT nao corrompe PII

    @Test
    @WithMockUser(roles = "ANALYST")
    void putWithMaskedValuesKeepsDocumentEmailAndPhone() throws Exception {
        String cpf = randomCpf();
        PartyResponse created = create(cpf);

        // exatamente o que o front envia: os valores mascarados devolvidos pelo GET
        String body = """
                {"type":"PF","document":"%s","name":"Maria Souza Editada","email":"%s","phone":"%s","motherName":"%s"}
                """.formatted(created.getDocument(), created.getEmail(), created.getPhone(), created.getMotherName());
        mvc.perform(put("/api/v1/parties/" + created.getId()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Maria Souza Editada"));

        var reloaded = partyRepository.findById(created.getId()).orElseThrow();
        assertEquals(cpf, reloaded.getDocument(), "CPF nao pode ser corrompido por valor mascarado");
        assertEquals("maria.souza@email.com", reloaded.getEmail());
        assertEquals("11987654321", reloaded.getPhone());
        assertEquals("Ana Paula Souza", reloaded.getMotherName());
        assertNotNull(partyRepository.findByDocumentHash(br.com.cactus.guard.common.util.DocumentHash.of(cpf)).orElse(null));
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void putWithoutDocumentIsAcceptedAndKeepsDocument() throws Exception {
        String cpf = randomCpf();
        PartyResponse created = create(cpf);
        mvc.perform(put("/api/v1/parties/" + created.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PF\",\"name\":\"Sem Documento\"}"))
                .andExpect(status().isOk());
        assertEquals(cpf, partyRepository.findById(created.getId()).orElseThrow().getDocument());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void putWithDifferentUnmaskedDocumentIs422() throws Exception {
        PartyResponse created = create(randomCpf());
        mvc.perform(put("/api/v1/parties/" + created.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PF\",\"name\":\"X\",\"document\":\"" + randomCpf() + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DOCUMENT_IMMUTABLE"));
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void putWithSameDocumentFormattedIsAccepted() throws Exception {
        String cpf = randomCpf();
        PartyResponse created = create(cpf);
        String formatted = cpf.substring(0, 3) + "." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-" + cpf.substring(9);
        mvc.perform(put("/api/v1/parties/" + created.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PF\",\"name\":\"Formatado\",\"document\":\"" + formatted + "\"}"))
                .andExpect(status().isOk());
        assertEquals(cpf, partyRepository.findById(created.getId()).orElseThrow().getDocument());
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void createWithoutDocumentIs400() throws Exception {
        mvc.perform(post("/api/v1/parties").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PF\",\"name\":\"Sem Doc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ------------------------------------------------------------ PartyResponse (spec 046)

    @Test
    @WithMockUser(roles = "ANALYST")
    void responseExposesSpec046FieldsWithMaskedPii() throws Exception {
        String cpf = randomCpf();
        PartyResponse created = create(cpf);
        mvc.perform(get("/api/v1/parties/" + created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document").value("*******" + cpf.substring(7)))
                .andExpect(jsonPath("$.email").value("m***a@email.com"))
                .andExpect(jsonPath("$.motherName").value("A*** P*** S***"))
                .andExpect(jsonPath("$.partyRole").value("CUSTOMER"))
                .andExpect(jsonPath("$.sourceType").value("ONBOARDED"))
                .andExpect(jsonPath("$.pep").value(false))
                .andExpect(jsonPath("$.pepStatus").value("NONE"))
                .andExpect(jsonPath("$.eddRequired").value(false))
                .andExpect(jsonPath("$.assetFreezeActive").value(false))
                .andExpect(jsonPath("$.declaredMonthlyIncome").value(5000.0))
                .andExpect(jsonPath("$.riskLevel").value("LOW"));
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void pepEndpointReturnsCondition() throws Exception {
        PartyResponse created = create(randomCpf());
        mvc.perform(get("/api/v1/parties/" + created.getId() + "/pep"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pep").value(false))
                .andExpect(jsonPath("$.pepStatus").value("NONE"));
    }

    // ------------------------------------------------------------ filtros

    @Test
    @WithMockUser(roles = "AUDITOR")
    void filtersByDocumentHashTypeRiskPepAndSource() throws Exception {
        String cpf = randomCpf();
        create(cpf);
        String base = "/api/v1/parties?document=" + cpf;
        mvc.perform(get(base)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(base + "&type=PF")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(base + "&personType=PJ")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(base + "&riskLevel=LOW")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(base + "&riskLevel=HIGH")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(base + "&pep=true")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(base + "&isPep=false")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(base + "&sourceType=ONBOARDED")).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(base + "&sourceType=MIGRATED")).andExpect(jsonPath("$.totalElements").value(0));
        // documento parcial/curinga nunca casa (hash exato, sem LIKE)
        mvc.perform(get("/api/v1/parties?document=" + cpf.substring(0, 6))).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/parties?document=%25")).andExpect(jsonPath("$.totalElements").value(0));
    }

    // ------------------------------------------------------------ reveal

    @Test
    @WithMockUser(roles = "ANALYST")
    void revealReturnsOnlyRequestedFieldAndLogsReason() throws Exception {
        String cpf = randomCpf();
        PartyResponse created = create(cpf);
        mvc.perform(post("/api/v1/parties/" + created.getId() + "/reveal").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"field\":\"document\",\"reason\":\"Conferencia para analise do caso 123\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.field").value("document"))
                .andExpect(jsonPath("$.value").value(cpf))
                .andExpect(jsonPath("$.values.email").doesNotExist());

        var logs = dataAccessLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("PARTY", created.getId(), Pageable.unpaged());
        assertTrue(logs.stream().anyMatch(l -> "document".equals(l.getFieldsAccessed())
                && l.getAccessReason() != null && l.getAccessReason().contains("Conferencia para analise do caso 123")));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void revealRequiresReasonAndValidField() throws Exception {
        PartyResponse created = create(randomCpf());
        String url = "/api/v1/parties/" + created.getId() + "/reveal";
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"field\":\"document\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"field\":\"document\",\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"field\":\"password\",\"reason\":\"motivo suficiente aqui\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"motivo suficiente aqui\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void auditorCanReveal() throws Exception {
        PartyResponse created = create(randomCpf());
        mvc.perform(post("/api/v1/parties/" + created.getId() + "/reveal").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fields\":[\"email\",\"phone\"],\"reason\":\"auditoria interna 2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values.email").value("maria.souza@email.com"))
                .andExpect(jsonPath("$.values.phone").value("11987654321"))
                .andExpect(jsonPath("$.values.document").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCannotReveal() throws Exception {
        mvc.perform(post("/api/v1/parties/" + UUID.randomUUID() + "/reveal").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"field\":\"document\",\"reason\":\"motivo suficiente aqui\"}"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ recalculo de risco

    @Test
    @WithMockUser(roles = "ANALYST")
    void recalculateRiskOfSingleParty() throws Exception {
        PartyResponse created = create(randomCpf());
        mvc.perform(post("/api/v1/parties/" + created.getId() + "/risk/recalculate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.riskScore").exists())
                .andExpect(jsonPath("$.nextReviewDate").exists());
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void recalculateRiskDeniedForAuditor() throws Exception {
        mvc.perform(post("/api/v1/parties/" + UUID.randomUUID() + "/risk/recalculate"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ------------------------------------------------------------ contrato de erro global

    @Test
    @WithMockUser(roles = "ANALYST")
    void errorBodyHasStableCodeAndTraceId() throws Exception {
        // traceId = traceId do OpenTelemetry (32 hex): o mesmo no header X-Trace-Id e no corpo de erro.
        // Um X-Trace-Id enviado pelo cliente nao vira o traceId (fica no span como client.trace_id).
        var result = mvc.perform(get("/api/v1/parties/" + UUID.randomUUID()).header("X-Trace-Id", "trace-abc-12345678"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").exists())
                .andReturn();
        String header = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(header);
        assertTrue(header.matches("[0-9a-f]{32}"), "traceId deve ser o do OpenTelemetry: " + header);
        assertTrue(result.getResponse().getContentAsString().contains("\"traceId\":\"" + header + "\""));
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void traceIdIsGeneratedWhenAbsentOrInvalid() throws Exception {
        var result = mvc.perform(get("/api/v1/parties/" + UUID.randomUUID()).header("X-Trace-Id", "x\ny"))
                .andExpect(status().isNotFound())
                .andReturn();
        String header = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(header);
        assertTrue(header.matches("[0-9a-f]{32}"), "sem contexto valido, o traceId e gerado pelo tracer");
        assertTrue(result.getResponse().getContentAsString().contains(header));
    }

    @Test
    void unauthenticatedStillCarriesTraceHeader() throws Exception {
        mvc.perform(get("/api/v1/parties"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void businessRuleCode() throws Exception {
        String cpf = randomCpf();
        create(cpf);
        mvc.perform(post("/api/v1/parties").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PF\",\"name\":\"Dup\",\"document\":\"" + cpf + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE"));
    }
}
