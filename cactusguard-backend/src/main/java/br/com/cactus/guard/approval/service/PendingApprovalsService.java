package br.com.cactus.guard.approval.service;

import br.com.cactus.guard.approval.dto.PendingApprovalsResponse;
import br.com.cactus.guard.approval.dto.PendingApprovalsResponse.CaseItem;
import br.com.cactus.guard.approval.dto.PendingApprovalsResponse.Counts;
import br.com.cactus.guard.approval.dto.PendingApprovalsResponse.PepItem;
import br.com.cactus.guard.approval.dto.PendingApprovalsResponse.RuleItem;
import br.com.cactus.guard.case_.entity.Case;
import br.com.cactus.guard.case_.repository.CaseRepository;
import br.com.cactus.guard.case_.service.SlaCalculator;
import br.com.cactus.guard.common.security.CurrentUserService;
import br.com.cactus.guard.common.util.DataMasker;
import br.com.cactus.guard.party.entity.Party;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Agrega o que aguarda segunda aprovacao: cases PENDING_APPROVAL, regras PENDING_APPROVAL e PEPs sem
 * aprovacao da alta administracao (art. 34). Somente leitura; a ordenacao e fixa (mais antigos primeiro).
 */
@Service
@RequiredArgsConstructor
public class PendingApprovalsService {

    private static final String PENDING = "PENDING_APPROVAL";
    private static final List<String> PEP_ACTIVE = List.of("ACTIVE", "RELATED");

    private final CaseRepository caseRepository;
    private final MonitoringRuleRepository ruleRepository;
    private final CurrentUserService currentUser;

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public PendingApprovalsResponse pending(Pageable pageable) {
        UUID me = currentUser.user().map(u -> u.getId()).orElse(null);
        Instant now = Instant.now();
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();

        Specification<Case> caseSpec = (r, q, cb) -> cb.equal(r.get("status"), PENDING);
        Page<CaseItem> cases = caseRepository.findAll(caseSpec,
                        PageRequest.of(page, size, Sort.by("analysisDeadline").ascending()))
                .map(c -> new CaseItem(c.getId(), c.getCaseNumber(),
                        c.getParty() != null ? c.getParty().getId() : null,
                        c.getParty() != null ? c.getParty().getName() : null,
                        c.getParty() != null ? c.getParty().getRiskLevel() : null,
                        c.getDecision(), c.getDecidedBy() != null ? c.getDecidedBy().getId() : null,
                        c.getDecidedAt(), c.isEdd(), SlaCalculator.classify(c, now),
                        c.getAnalysisDeadline(),
                        me != null && c.getDecidedBy() != null && me.equals(c.getDecidedBy().getId())));

        Specification<MonitoringRule> ruleSpec = (r, q, cb) -> cb.equal(r.get("status"), PENDING);
        Page<RuleItem> rules = ruleRepository.findAll(ruleSpec, PageRequest.of(page, size, Sort.by("code").ascending()))
                .map(r -> new RuleItem(r.getCode(), r.getName(), r.getCategory(), r.getSeverity(), r.getVersion(),
                        r.getCreatedBy() != null ? r.getCreatedBy().getId() : null,
                        r.getCreatedBy() != null ? r.getCreatedBy().getName() : null,
                        r.getUpdatedAt() != null ? r.getUpdatedAt() : r.getCreatedAt(),
                        me != null && r.getCreatedBy() != null && me.equals(r.getCreatedBy().getId())));

        Page<PepItem> peps = pendingPeps(page, size);
        return new PendingApprovalsResponse(cases, rules, peps,
                new Counts(cases.getTotalElements(), rules.getTotalElements(), peps.getTotalElements()));
    }

    /** PEPs marcados (condicao vigente) sem aprovacao da alta administracao. */
    private Page<PepItem> pendingPeps(int page, int size) {
        String where = " FROM Party p WHERE p.pep = true AND p.pepApprovalBy IS NULL"
                + " AND p.pepStatus IN :sts";
        long total = em.createQuery("SELECT COUNT(p)" + where, Long.class).setParameter("sts", PEP_ACTIVE).getSingleResult();
        PageRequest req = PageRequest.of(page, size);
        List<PepItem> items = em.createQuery("SELECT p" + where + " ORDER BY p.createdAt ASC", Party.class)
                .setParameter("sts", PEP_ACTIVE)
                .setFirstResult((int) req.getOffset())
                .setMaxResults(size)
                .getResultList().stream()
                .map(p -> new PepItem(p.getId(), p.getName(), DataMasker.maskDocument(p.getDocument()),
                        p.getPepCategory(), p.getPepRole(), p.getPepStartDate(), p.getRiskLevel(),
                        p.getUpdatedAt() != null ? p.getUpdatedAt() : p.getCreatedAt()))
                .toList();
        return new PageImpl<>(items, req, total);
    }
}
