package br.com.cactus.guard.audit;

import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.audit.service.DataAccessService;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.kyc.service.KycService;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.support.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Auditoria / acesso a dados: DTO (sem entity), PII mascarada em old/new, filtros server-side e KYC por parte. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditFilterContractTest {

    @Autowired private MockMvc mvc;
    @Autowired private AuditService auditService;
    @Autowired private DataAccessService dataAccessService;
    @Autowired private UserRepository userRepository;
    @Autowired private PartyRepository partyRepository;
    @Autowired private KycService kycService;

    private org.springframework.test.web.servlet.ResultActions auditor(String url) throws Exception {
        return mvc.perform(get(url).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user("aud-reader").roles("AUDITOR")));
    }

    @AfterEach
    void logout() {
        TestUsers.logout();
    }

    @Test
    void auditResponseMasksPiiInOldAndNewAndFiltersApply() throws Exception {
        TestUsers.ensure(userRepository, "auditor-x");
        TestUsers.login("auditor-x", "AUDITOR");
        UUID entityId = UUID.randomUUID();
        auditService.log("PARTY_UPDATED", "PARTY", entityId,
                Map.of("email", "maria@email.com", "name", "Maria"),
                Map.of("document", "12345678901", "documentHash", "abc123", "phone", "11987654321",
                        "motherName", "Ana Paula Souza", "password", "segredo"));
        TestUsers.logout();

        String base = "/api/v1/audit?entityId=" + entityId;
        auditor(base)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("PARTY_UPDATED"))
                .andExpect(jsonPath("$.content[0].user.username").value("auditor-x"))
                .andExpect(jsonPath("$.content[0].oldValue.email").value("m***a@email.com"))
                .andExpect(jsonPath("$.content[0].oldValue.name").value("Maria"))
                .andExpect(jsonPath("$.content[0].newValue.document").value("*******8901"))
                .andExpect(jsonPath("$.content[0].newValue.documentHash").value("abc123"))
                .andExpect(jsonPath("$.content[0].newValue.phone").value("***4321"))
                .andExpect(jsonPath("$.content[0].newValue.motherName").value("A*** P*** S***"))
                .andExpect(jsonPath("$.content[0].newValue.password").value("***"))
                .andExpect(jsonPath("$.content[0].createdAt").exists());

        auditor(base + "&username=auditor-x").andExpect(jsonPath("$.totalElements").value(1));
        auditor(base + "&username=outro").andExpect(jsonPath("$.totalElements").value(0));
        auditor(base + "&action=PARTY_UPDATED&entityType=PARTY").andExpect(jsonPath("$.totalElements").value(1));
        auditor(base + "&action=OUTRA").andExpect(jsonPath("$.totalElements").value(0));
        auditor(base + "&entityType=CASE").andExpect(jsonPath("$.totalElements").value(0));
        auditor(base + "&from=2999-01-01").andExpect(jsonPath("$.totalElements").value(0));
        auditor(base + "&to=2000-01-01").andExpect(jsonPath("$.totalElements").value(0));
        auditor(base + "&from=2000-01-01&to=2999-12-31").andExpect(jsonPath("$.totalElements").value(1));
        auditor(base + "&from=ontem").andExpect(status().isBadRequest());
        auditor("/api/v1/audit/entity/PARTY/" + entityId).andExpect(jsonPath("$.content[0].action").value("PARTY_UPDATED"));
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void auditDeniedForAnalyst() throws Exception {
        mvc.perform(get("/api/v1/audit")).andExpect(status().isForbidden());
    }

    @Test
    void dataAccessIsDtoWithServerSideFilters() throws Exception {
        TestUsers.ensure(userRepository, "lgpd-user");
        TestUsers.login("lgpd-user", "ANALYST");
        UUID entityId = UUID.randomUUID();
        dataAccessService.log("PARTY", entityId, "document", "REVELACAO: motivo de teste", "10.0.0.1");
        TestUsers.logout();

        auditor("/api/v1/data-access?entityId=" + entityId + "&username=lgpd-user&entityType=PARTY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fieldsAccessed").value("document"))
                .andExpect(jsonPath("$.content[0].accessReason").value("REVELACAO: motivo de teste"))
                .andExpect(jsonPath("$.content[0].user.username").value("lgpd-user"));
        auditor("/api/v1/data-access?entityId=" + entityId + "&username=outro")
                .andExpect(jsonPath("$.totalElements").value(0));
        auditor("/api/v1/data-access/entity/PARTY/" + entityId)
                .andExpect(jsonPath("$.content[0].user.username").value("lgpd-user"));
    }

    @Test
    @WithMockUser(roles = "ANALYST")
    void kycListFiltersByPartyAcrossStatuses() throws Exception {
        Party p = partyRepository.save(Party.builder().type("PF")
                .document(String.format("%011d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 99_999_999_999L)))
                .name("Kyc Party").status("ACTIVE").build());
        kycService.startOnboarding(p.getId());

        mvc.perform(get("/api/v1/kyc?partyId=" + p.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].partyId").value(p.getId().toString()));
        mvc.perform(get("/api/v1/kyc?partyId=" + UUID.randomUUID()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/kyc?partyId=" + p.getId() + "&status=APPROVED"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
