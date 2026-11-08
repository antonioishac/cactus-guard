package br.com.cactus.guard.siscoaf.service;

import br.com.cactus.guard.case_.entity.Case;
import br.com.cactus.guard.case_.repository.CaseRepository;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.siscoaf.entity.SiscoafCommunication;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Legado: SAR equivale a COS. Gera (ou recupera) o rascunho de COS de um case cuja decisao
 * COMMUNICATE ja foi aprovada. O payload referencia a parte por id, nunca por documento.
 */
@Service
@RequiredArgsConstructor
public class SarGeneratorService {

    private final CaseRepository caseRepository;
    private final SiscoafDraftService draftService;

    @Transactional
    public SiscoafCommunication generateSar(UUID caseId) {
        Case c = caseRepository.findById(caseId)
                .orElseThrow(() -> new BusinessException("Case não encontrado"));

        if (!"COMMUNICATE".equals(c.getStatus()) && !"COMMUNICATED".equals(c.getStatus())) {
            throw new BusinessException("COS/SAR só pode ser gerada para case com decisão COMMUNICATE aprovada");
        }
        return draftService.createCosDraft(c, c.getApprovedAt(), c.getCommunicationDeadline());
    }
}
