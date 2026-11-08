package br.com.cactus.guard.kyc.service;

import br.com.cactus.guard.kyc.repository.KycDocumentRepository;

import br.com.cactus.guard.kyc.dto.KycDocumentResponse;
import br.com.cactus.guard.kyc.entity.KycDocument;
import br.com.cactus.guard.kyc.repository.KycProcessRepository;
import br.com.cactus.guard.audit.service.DataAccessService;
import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KycDocumentService {

    private final KycDocumentRepository documentRepository;
    private final KycProcessRepository processRepository;
    private final DataAccessService dataAccessService;
    private final AuditService auditService;

    @Value("${kyc.upload.max-bytes:10485760}")
    private long maxBytes;

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "application/pdf", Set.of("pdf"),
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"));

    @Transactional
    public KycDocumentResponse addDocument(UUID processId, String documentType, String fileName, byte[] content, String contentType) {
        var process = processRepository.findById(processId)
                .orElseThrow(() -> new BusinessException("KYC process not found"));

        String safeName = validateUpload(fileName, content, contentType);

        KycDocument doc = KycDocument.builder()
                .process(process)
                .documentType(documentType)
                .fileName(safeName)
                .content(content)
                .contentType(contentType.toLowerCase(Locale.ROOT).trim())
                .sha256(sha256(content))
                .sizeBytes((long) content.length)
                .status("PENDING")
                .build();

        doc = documentRepository.save(doc);
        auditService.log("KYC_DOCUMENT_UPLOADED", "KYC_DOCUMENT", doc.getId(), null,
                Map.of("processId", processId.toString(), "sha256", doc.getSha256(), "sizeBytes", doc.getSizeBytes()));
        return toResponse(doc);
    }

    /** Valida tipo (content-type + extensao + assinatura), tamanho e devolve o nome sanitizado. */
    private String validateUpload(String fileName, byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            throw new BusinessException("Arquivo vazio");
        }
        if (content.length > maxBytes) {
            throw new BusinessException("Arquivo excede o tamanho maximo de " + maxBytes + " bytes");
        }
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        Set<String> exts = ALLOWED.get(ct);
        if (exts == null) {
            throw new BusinessException("Tipo de arquivo nao permitido (aceitos: PDF, JPG, PNG)");
        }
        String safe = sanitizeFileName(fileName);
        String ext = safe.contains(".") ? safe.substring(safe.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!exts.contains(ext)) {
            throw new BusinessException("Extensao do arquivo incompativel com o tipo informado");
        }
        if (!matchesSignature(ct, content)) {
            throw new BusinessException("Conteudo do arquivo nao corresponde ao tipo informado");
        }
        return safe;
    }

    static String sanitizeFileName(String original) {
        String name = original == null ? "" : original;
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);          // sem diretorios (path traversal)
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");           // sem caracteres de controle/CRLF/aspas
        name = name.replaceAll("\\.{2,}", ".");
        if (name.length() > 100) {
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "";
            name = name.substring(0, 100 - ext.length()) + ext;
        }
        if (name.isBlank() || name.startsWith(".")) {
            throw new BusinessException("Nome de arquivo invalido");
        }
        return name;
    }

    private static boolean matchesSignature(String ct, byte[] c) {
        return switch (ct) {
            case "application/pdf" -> c.length > 4 && c[0] == '%' && c[1] == 'P' && c[2] == 'D' && c[3] == 'F' && c[4] == '-';
            case "image/jpeg" -> c.length > 3 && (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8 && (c[2] & 0xFF) == 0xFF;
            case "image/png" -> c.length > 7 && (c[0] & 0xFF) == 0x89 && c[1] == 'P' && c[2] == 'N' && c[3] == 'G';
            default -> false;
        };
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }

    @Transactional(readOnly = true)
    public List<KycDocumentResponse> findByProcess(UUID processId) {
        dataAccessService.log("KYC_DOCUMENT", processId, "fileName,content", "CONSULTA", null);
        return documentRepository.findByProcessId(processId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public KycDocumentResponse approve(UUID documentId) {
        KycDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        doc.setStatus("APPROVED");
        doc.setReviewedAt(java.time.Instant.now());
        doc = documentRepository.save(doc);
        auditService.log("KYC_DOCUMENT_APPROVED", "KYC_DOCUMENT", doc.getId(), Map.of("status", "PENDING"), Map.of("status", "APPROVED"));
        return toResponse(doc);
    }

    private KycDocumentResponse toResponse(KycDocument doc) {
        return KycDocumentResponse.builder()
                .id(doc.getId())
                .processId(doc.getProcess().getId())
                .documentType(doc.getDocumentType())
                .fileName(doc.getFileName())
                .status(doc.getStatus())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
