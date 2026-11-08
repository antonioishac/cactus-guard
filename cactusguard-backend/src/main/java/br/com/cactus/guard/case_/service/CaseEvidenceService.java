package br.com.cactus.guard.case_.service;

import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.audit.service.DataAccessService;
import br.com.cactus.guard.case_.dto.CaseEvidenceResponse;
import br.com.cactus.guard.case_.entity.Case;
import br.com.cactus.guard.case_.entity.CaseEvidence;
import br.com.cactus.guard.case_.repository.CaseEvidenceRepository;
import br.com.cactus.guard.case_.repository.CaseRepository;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.common.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Evidencias do dossie: arquivo em disco (diretorio configuravel, fora do webroot) + registro imutavel
 * com sha256. O download revalida o hash (integridade) e registra o acesso (LGPD).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseEvidenceService {

    /** Cases encerrados tem o dossie congelado. */
    private static final Set<String> CLOSED = Set.of("ARCHIVED", "COMMUNICATED");

    private final CaseEvidenceRepository evidenceRepository;
    private final CaseRepository caseRepository;
    private final AuditService auditService;
    private final DataAccessService dataAccessService;
    private final CurrentUserService currentUser;

    @Value("${cases.evidence.dir:data/case-evidences}")
    private String baseDirName;

    @Value("${cases.evidence.max-bytes:10485760}")
    private long maxBytes;

    static void assertMutable(Case c) {
        if (CLOSED.contains(c.getStatus())) {
            throw new BusinessException("Case encerrado: o dossie nao aceita novos itens");
        }
    }

    /** Download de arquivo validado. */
    public record EvidenceFile(byte[] content, String fileName, String contentType) {
    }

    @Transactional(readOnly = true)
    public List<CaseEvidenceResponse> list(UUID caseId) {
        if (!caseRepository.existsById(caseId)) {
            throw new BusinessException("Case not found");
        }
        return evidenceRepository.findByCaseEntityIdOrderByCreatedAtAsc(caseId).stream()
                .map(CaseEvidenceService::toResponse).toList();
    }

    @Transactional
    public CaseEvidenceResponse upload(UUID caseId, String fileName, String contentType, byte[] content,
                                       String description) {
        Case c = caseRepository.findById(caseId).orElseThrow(() -> new BusinessException("Case not found"));
        assertMutable(c);
        String safeName = EvidenceUploadValidator.validate(fileName, content, contentType, maxBytes);
        String sha = EvidenceUploadValidator.sha256(content);
        if (evidenceRepository.existsByCaseEntityIdAndSha256(caseId, sha)) {
            throw new BusinessException("Arquivo ja anexado a este case (mesmo sha256)");
        }

        UUID evidenceId = UUID.randomUUID();
        Path base = baseDir();
        Path target = base.resolve(caseId.toString()).resolve(evidenceId + "_" + safeName).normalize();
        if (!target.startsWith(base)) {
            throw new BusinessException("Nome de arquivo invalido");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            log.error("Falha ao gravar evidencia do case {}", caseId, e);
            throw new BusinessException("Nao foi possivel armazenar a evidencia");
        }

        try {
            CaseEvidence ev = evidenceRepository.save(CaseEvidence.builder()
                    .caseEntity(c)
                    .fileName(safeName)
                    .filePath(base.relativize(target).toString())
                    .contentType(EvidenceUploadValidator.normalizeType(contentType))
                    .sha256(sha)
                    .sizeBytes((long) content.length)
                    .description(description != null && !description.isBlank() ? description.trim() : null)
                    .uploadedBy(currentUser.user().orElse(null))
                    .build());
            auditService.log("CASE_EVIDENCE_UPLOADED", "CASE", caseId, null,
                    Map.of("evidenceId", ev.getId().toString(), "sha256", sha, "sizeBytes", content.length,
                            "fileName", safeName));
            return toResponse(ev);
        } catch (RuntimeException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                log.warn("Arquivo orfao de evidencia nao removido (case {})", caseId);
            }
            throw e;
        }
    }

    /** Nao e readOnly: registra o acesso (LGPD) e a auditoria do download. */
    @Transactional
    public EvidenceFile download(UUID caseId, UUID evidenceId) {
        CaseEvidence ev = evidenceRepository.findById(evidenceId)
                .filter(e -> e.getCaseEntity().getId().equals(caseId))
                .orElseThrow(() -> new BusinessException("Evidencia nao encontrada"));
        Path base = baseDir();
        Path file = base.resolve(ev.getFilePath()).normalize();
        if (!file.startsWith(base) || !Files.isRegularFile(file)) {
            log.warn("Evidencia {} indisponivel no armazenamento", evidenceId);
            throw new BusinessException("Arquivo da evidencia indisponivel");
        }
        byte[] content;
        try {
            content = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new BusinessException("Arquivo da evidencia indisponivel");
        }
        if (!EvidenceUploadValidator.sha256(content).equals(ev.getSha256())) {
            log.error("Integridade da evidencia {} violada (sha256 divergente)", evidenceId);
            auditService.log("CASE_EVIDENCE_INTEGRITY_FAILURE", "CASE", caseId, null,
                    Map.of("evidenceId", evidenceId.toString()));
            throw new BusinessException("Integridade da evidencia comprometida (sha256 divergente)");
        }
        dataAccessService.log("CASE_EVIDENCE", evidenceId, "fileName,content", "DOWNLOAD_EVIDENCIA", null);
        auditService.log("CASE_EVIDENCE_DOWNLOADED", "CASE", caseId, null, Map.of("evidenceId", evidenceId.toString()));
        return new EvidenceFile(content, ev.getFileName(),
                ev.getContentType() != null ? ev.getContentType() : "application/octet-stream");
    }

    private Path baseDir() {
        return Path.of(baseDirName).toAbsolutePath().normalize();
    }

    private static CaseEvidenceResponse toResponse(CaseEvidence e) {
        return new CaseEvidenceResponse(e.getId(), e.getFileName(), e.getContentType(),
                e.getSizeBytes() != null ? e.getSizeBytes() : 0L, e.getSha256(), e.getDescription(),
                e.getUploadedBy() != null ? e.getUploadedBy().getId() : null,
                e.getUploadedBy() != null ? e.getUploadedBy().getName() : null, e.getCreatedAt());
    }
}
