package br.com.cactus.guard.security;

import br.com.cactus.guard.auth.entity.Role;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.auth.repository.RoleRepository;
import br.com.cactus.guard.auth.repository.UserRepository;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.kyc.entity.KycProcess;
import br.com.cactus.guard.kyc.service.KycDocumentService;
import br.com.cactus.guard.kyc.service.KycService;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.party.repository.PartyRepository;
import br.com.cactus.guard.report.entity.ReportJob;
import br.com.cactus.guard.report.repository.ReportJobRepository;
import br.com.cactus.guard.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ReportAndKycUploadSecurityTest {

    @Autowired ReportService reportService;
    @Autowired ReportJobRepository jobRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PartyRepository partyRepository;
    @Autowired KycService kycService;
    @Autowired KycDocumentService documentService;

    private User owner() {
        String name = "owner-" + UUID.randomUUID().toString().substring(0, 8);
        Role analyst = roleRepository.findByName("ANALYST").orElseThrow();
        return userRepository.save(User.builder().username(name).email(name + "@x.com")
                .password("x").name("Owner").active(true).roles(Set.of(analyst)).build());
    }

    private ReportJob job(User u, String path) {
        return jobRepository.save(ReportJob.builder().reportCode("ALERTS_SUMMARY").requestedBy(u)
                .status("COMPLETED").format("PDF").filePath(path).build());
    }

    @Test
    @WithMockUser(username = "outro-analista", roles = "ANALYST")
    void nonOwnerCannotReadOrDownloadJob() {
        ReportJob j = job(owner(), null);
        assertThrows(AccessDeniedException.class, () -> reportService.findJob(j.getId()));
        assertThrows(AccessDeniedException.class, () -> reportService.resolveDownload(j.getId()));
    }

    @Test
    @WithMockUser(username = "qualquer", roles = "SUPERVISOR")
    void supervisorCanReadJobAndResponseHidesPath() {
        ReportJob j = job(owner(), "reports/x.pdf");
        var resp = reportService.findJob(j.getId());
        assertTrue(resp.isDownloadable());
    }

    @Test
    @WithMockUser(username = "qualquer", roles = "ADMIN")
    void pathOutsideReportsDirIsBlocked() {
        ReportJob j = job(owner(), "pom.xml"); // existe, mas fora de reports/
        assertThrows(RuntimeException.class, () -> reportService.resolveDownload(j.getId()));
    }

    private UUID process() {
        Party p = partyRepository.save(Party.builder().type("PF")
                .document("1" + System.nanoTime() % 10000000000L).documentHash(UUID.randomUUID().toString().replace("-", ""))
                .name("Up").status("ACTIVE").build());
        KycProcess kp = null;
        return kycService.startOnboarding(p.getId()).getId();
    }

    @Test
    void uploadRejectsDisallowedTypeSpoofedContentAndOversize() {
        UUID pid = process();
        byte[] pdf = "%PDF-1.4 ok".getBytes(StandardCharsets.UTF_8);
        assertThrows(BusinessException.class, () -> documentService.addDocument(pid, "RG", "a.exe", pdf, "application/x-msdownload"));
        assertThrows(BusinessException.class, () -> documentService.addDocument(pid, "RG", "a.pdf", "MZ...".getBytes(), "application/pdf"));
        assertThrows(BusinessException.class, () -> documentService.addDocument(pid, "RG", "a.png", pdf, "application/pdf"));
        assertThrows(BusinessException.class, () -> documentService.addDocument(pid, "RG", "a.pdf", new byte[0], "application/pdf"));
    }

    @Test
    void uploadSanitizesNameAndStoresSha256() {
        UUID pid = process();
        byte[] pdf = "%PDF-1.4 ok".getBytes(StandardCharsets.UTF_8);
        var doc = documentService.addDocument(pid, "RG", "../../etc/pass wd\r\n.pdf", pdf, "application/pdf");
        assertEquals("pass_wd__.pdf", doc.getFileName());
    }
}
