package br.com.cactus.guard.kyc.service;

import br.com.cactus.guard.kyc.dto.KycDocumentResponse;
import br.com.cactus.guard.kyc.entity.KycDocument;
import br.com.cactus.guard.kyc.repository.KycProcessRepository;
import br.com.cactus.guard.audit.service.DataAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KycDocumentService {

    private final KycDocumentRepository documentRepository;
    private final KycProcessRepository processRepository;
    private final DataAccessService dataAccessService;

    @Transactional
    public KycDocumentResponse addDocument(UUID processId, String documentType, String fileName, byte[] content, String contentType) {
        var process = processRepository.findById(processId)
                .orElseThrow(() -> new br.com.cactus.guard.common.exception.BusinessException("KYC process not found"));

        KycDocument doc = KycDocument.builder()
                .process(process)
                .documentType(documentType)
                .fileName(fileName)
                .content(content)
                .contentType(contentType)
                .status("PENDING")
                .build();

        doc = documentRepository.save(doc);
        return toResponse(doc);
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
        doc = documentRepository.save(doc);
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
