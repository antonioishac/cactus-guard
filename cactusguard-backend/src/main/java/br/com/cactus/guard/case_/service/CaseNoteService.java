package br.com.cactus.guard.case_.service;

import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.case_.dto.CaseNoteResponse;
import br.com.cactus.guard.case_.entity.Case;
import br.com.cactus.guard.case_.entity.CaseNote;
import br.com.cactus.guard.case_.repository.CaseNoteRepository;
import br.com.cactus.guard.case_.repository.CaseRepository;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.common.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Notas de analise do case: append-only (sem edicao/exclusao); cada nota e auditada. */
@Service
@RequiredArgsConstructor
public class CaseNoteService {

    private final CaseNoteRepository noteRepository;
    private final CaseRepository caseRepository;
    private final AuditService auditService;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public List<CaseNoteResponse> list(UUID caseId) {
        if (!caseRepository.existsById(caseId)) {
            throw new BusinessException("Case not found");
        }
        return noteRepository.findByCaseEntityIdOrderByCreatedAtAsc(caseId).stream().map(CaseNoteService::toResponse).toList();
    }

    @Transactional
    public CaseNoteResponse add(UUID caseId, String text) {
        Case c = caseRepository.findById(caseId).orElseThrow(() -> new BusinessException("Case not found"));
        if (text == null || text.isBlank()) {
            throw new BusinessException("Nota nao pode ser vazia");
        }
        CaseEvidenceService.assertMutable(c);
        CaseNote note = noteRepository.save(CaseNote.builder()
                .caseEntity(c)
                .author(currentUser.user().orElse(null))
                .content(text.trim())
                .build());
        // o conteudo da nota nao vai para o audit_log (pode conter dados sensiveis); apenas o fato e o tamanho
        auditService.log("CASE_NOTE_ADDED", "CASE", caseId, null,
                Map.of("noteId", note.getId().toString(), "length", note.getContent().length()));
        return toResponse(note);
    }

    static CaseNoteResponse toResponse(CaseNote n) {
        return new CaseNoteResponse(n.getId(), n.getAuthor() != null ? n.getAuthor().getId() : null,
                n.getAuthor() != null ? n.getAuthor().getName() : null, n.getContent(), n.getCreatedAt());
    }
}
