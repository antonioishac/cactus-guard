package br.com.cactus.guard.siscoaf.service;

import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.common.calendar.BusinessCalendar;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.common.security.CurrentUserService;
import br.com.cactus.guard.siscoaf.entity.SiscoafCommunication;
import br.com.cactus.guard.siscoaf.repository.SiscoafCommunicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;

/** Declaracao negativa anual: so existe se nenhuma COS/COE foi enviada no ano de referencia. */
@Service
@RequiredArgsConstructor
public class NegativeDeclarationService {

    private final SiscoafCommunicationRepository repository;
    private final AuditService auditService;
    private final CurrentUserService currentUser;

    @Transactional
    public SiscoafCommunication generate(int referenceYear) {
        int currentYear = Year.now(BusinessCalendar.BRAZIL).getValue();
        if (referenceYear > currentYear || referenceYear < 2000) {
            throw new BusinessException("Ano de referencia invalido: " + referenceYear);
        }
        if (repository.existsByTypeAndReferenceYearAndStatusNotIn("NEGATIVE_DECLARATION", referenceYear,
                List.of("CANCELLED", "RECTIFIED", "REJECTED"))) {
            throw new BusinessException("Ja existe declaracao negativa vigente para " + referenceYear);
        }
        Instant from = LocalDate.of(referenceYear, 1, 1).atStartOfDay(BusinessCalendar.BRAZIL).toInstant();
        Instant to = LocalDate.of(referenceYear + 1, 1, 1).atStartOfDay(BusinessCalendar.BRAZIL).toInstant();
        long sent = repository.countSentBetween(from, to);
        if (sent > 0) {
            throw new BusinessException("Declaracao negativa nao permitida: ha " + sent
                    + " comunicacao(oes) COS/COE enviada(s) em " + referenceYear);
        }
        SiscoafCommunication decl = SiscoafCommunication.builder()
                .type("NEGATIVE_DECLARATION")
                .status("DRAFT")
                .referenceYear(referenceYear)
                .payload(Map.of("referenceYear", referenceYear))
                .createdBy(currentUser.user().orElse(null))
                .build();
        decl = repository.save(decl);
        auditService.log("NEGATIVE_DECLARATION_CREATED", "SISCOAF_COMMUNICATION", decl.getId(), null,
                Map.of("referenceYear", referenceYear));
        return decl;
    }
}
