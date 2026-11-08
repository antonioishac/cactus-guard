package br.com.cactus.guard.rule.service;

import br.com.cactus.guard.common.audit.AuditPayload;
import br.com.cactus.guard.audit.service.AuditService;
import br.com.cactus.guard.auth.entity.User;
import br.com.cactus.guard.common.exception.BusinessException;
import br.com.cactus.guard.common.security.CurrentUserService;
import br.com.cactus.guard.common.validation.Justifications;
import br.com.cactus.guard.rule.dto.MonitoringRuleResponse;
import br.com.cactus.guard.rule.dto.RuleVersionResponse;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.rule.entity.MonitoringRuleVersion;
import br.com.cactus.guard.rule.entity.MonitoringRuleTemplate;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import br.com.cactus.guard.rule.repository.MonitoringRuleVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Governanca de regras: toda alteracao exige justificativa e gera nova versao imutavel
 * (snapshot em monitoring_rule_versions, gravado na aprovacao); a ativacao exige aprovacao
 * por usuario diferente do criador/editor. Enquanto aguarda aprovacao a regra fica inativa
 * (o motor nunca opera com parametros nao aprovados) e {@code active} acompanha {@code status}.
 */
@Service
@RequiredArgsConstructor
public class MonitoringRuleService {

    private final MonitoringRuleRepository ruleRepository;
    private final MonitoringRuleVersionRepository versionRepository;
    private final br.com.cactus.guard.rule.repository.MonitoringRuleTemplateRepository templateRepository;
    private final AuditService auditService;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    @Cacheable(value = "rules", key = "'active'")
    public List<MonitoringRuleResponse> findAllActive() {
        // ArrayList (nao Stream#toList): a lista imutavel do JDK e final e sai sem type id no
        // JSON polimorfico do cache Redis, o que impede a desserializacao
        return ruleRepository.findByActiveTrue().stream()
                .map(this::toResponse)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "rules", key = "'category:' + #category")
    public List<MonitoringRuleResponse> findByCategory(String category) {
        return ruleRepository.findByCategoryAndActiveTrue(category).stream()
                .map(this::toResponse)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    /** Cria a regra em PENDING_APPROVAL (inativa) - exige aprovacao de outro usuario. */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "rules", key = "'active'"),
            @CacheEvict(value = "rules", allEntries = true)
    })
    public MonitoringRuleResponse create(MonitoringRule rule) {
        rule.setStatus("PENDING_APPROVAL");
        rule.setActive(false);
        if (rule.getVersion() < 1) {
            rule.setVersion(1);
        }
        rule.setCreatedBy(currentUser.user().orElse(null));
        rule.setApprovedBy(null);
        rule.setApprovedAt(null);
        rule.setEffectiveFrom(null);
        rule.setEffectiveTo(null);
        rule = ruleRepository.save(rule);
        AuditPayload.log(auditService, "RULE_CREATED", "MONITORING_RULE", rule.getId(), null, state(rule));
        return toResponse(rule);
    }

    /**
     * Altera a regra (justificativa obrigatoria). Regra ja aprovada tem a versao vigente encerrada
     * e volta para PENDING_APPROVAL; a nova versao so vigora apos aprovacao de outro usuario.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "rules", key = "'active'"),
            @CacheEvict(value = "rules", allEntries = true)
    })
    public MonitoringRuleResponse update(String code, MonitoringRule updated, String reason) {
        String just = Justifications.require(reason, "alterar regra de monitoramento");
        MonitoringRule rule = find(code);
        Map<String, Object> before = state(rule);
        Instant now = Instant.now();

        if ("ACTIVE".equals(rule.getStatus()) || "INACTIVE".equals(rule.getStatus())) {
            closeOpenVersions(rule, now, null);
        }
        if (updated.getName() != null) rule.setName(updated.getName());
        if (updated.getDescription() != null) rule.setDescription(updated.getDescription());
        if (updated.getSeverity() != null) rule.setSeverity(updated.getSeverity());
        if (updated.getThresholdValue() != null) rule.setThresholdValue(updated.getThresholdValue());
        if (updated.getThresholdPeriodDays() != null) rule.setThresholdPeriodDays(updated.getThresholdPeriodDays());
        if (updated.getParameters() != null) rule.setParameters(updated.getParameters());
        if (updated.getAppliesToRiskLevels() != null) rule.setAppliesToRiskLevels(updated.getAppliesToRiskLevels());
        if (updated.getCarta4001Reference() != null) rule.setCarta4001Reference(updated.getCarta4001Reference());

        rule.setStatus("PENDING_APPROVAL");
        rule.setActive(false);
        rule.setCreatedBy(currentUser.user().orElse(null));
        rule.setApprovedBy(null);
        rule.setApprovedAt(null);
        rule.setEffectiveFrom(null);
        rule.setEffectiveTo(null);
        rule = ruleRepository.save(rule);

        Map<String, Object> after = state(rule);
        after.put("changeReason", just);
        AuditPayload.log(auditService, "RULE_UPDATED", "MONITORING_RULE", rule.getId(), before, after);
        return toResponse(rule);
    }

    /** Aprovacao (quatro olhos): usuario diferente de quem criou/editou. Grava a versao imutavel. */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "rules", key = "'active'"),
            @CacheEvict(value = "rules", allEntries = true)
    })
    public MonitoringRuleResponse approve(String code, String comment) {
        if (!currentUser.hasRole("SUPERVISOR")) {
            throw new AccessDeniedException("Aprovacao de regra exige perfil SUPERVISOR");
        }
        MonitoringRule rule = find(code);
        if (!"PENDING_APPROVAL".equals(rule.getStatus())) {
            throw new BusinessException("Regra nao esta aguardando aprovacao (status " + rule.getStatus() + ")");
        }
        User approver = currentUser.user()
                .orElseThrow(() -> new BusinessException("Aprovador nao identificado"));
        if (rule.getCreatedBy() != null && rule.getCreatedBy().getId().equals(approver.getId())) {
            throw new BusinessException("Aprovacao deve ser feita por usuario diferente de quem criou/alterou a regra");
        }

        Map<String, Object> before = state(rule);
        Instant now = Instant.now();
        // Versao ja registrada (ex.: snapshot inicial V27 ou reativacao): a aprovacao gera a proxima
        if (versionRepository.findByRuleIdAndVersion(rule.getId(), rule.getVersion()).isPresent()) {
            rule.setVersion(rule.getVersion() + 1);
        }
        closeOpenVersions(rule, now, rule.getVersion());

        rule.setStatus("ACTIVE");
        rule.setActive(true);
        rule.setApprovedBy(approver);
        rule.setApprovedAt(now);
        rule.setEffectiveFrom(now);
        rule.setEffectiveTo(null);
        rule = ruleRepository.save(rule);

        MonitoringRuleVersion version = MonitoringRuleVersion.builder()
                .rule(rule)
                .version(rule.getVersion())
                .snapshot(state(rule))
                .changeReason(comment != null && !comment.isBlank() ? comment.trim() : "Aprovacao de regra")
                .createdBy(rule.getCreatedBy())
                .approvedBy(approver)
                .approvedAt(now)
                .effectiveFrom(now)
                .build();
        versionRepository.save(version);

        AuditPayload.log(auditService, "RULE_APPROVED", "MONITORING_RULE", rule.getId(), before, state(rule));
        return toResponse(rule);
    }

    /** Desativa a regra (justificativa obrigatoria); encerra a vigencia da versao atual. */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "rules", key = "'active'"),
            @CacheEvict(value = "rules", allEntries = true)
    })
    public MonitoringRuleResponse deactivate(String code, String reason) {
        String just = Justifications.require(reason, "desativar regra de monitoramento");
        MonitoringRule rule = find(code);
        if (!"ACTIVE".equals(rule.getStatus())) {
            throw new BusinessException("Somente regra ACTIVE pode ser desativada (status " + rule.getStatus() + ")");
        }
        Map<String, Object> before = state(rule);
        Instant now = Instant.now();
        closeOpenVersions(rule, now, null);
        rule.setStatus("INACTIVE");
        rule.setActive(false);
        rule.setEffectiveTo(now);
        rule = ruleRepository.save(rule);
        Map<String, Object> after = state(rule);
        after.put("changeReason", just);
        AuditPayload.log(auditService, "RULE_DEACTIVATED", "MONITORING_RULE", rule.getId(), before, after);
        return toResponse(rule);
    }

    /** Solicita reativacao de regra INACTIVE: volta a PENDING_APPROVAL (aprovacao por outro usuario). */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "rules", key = "'active'"),
            @CacheEvict(value = "rules", allEntries = true)
    })
    public MonitoringRuleResponse requestActivation(String code, String reason) {
        String just = Justifications.require(reason, "reativar regra de monitoramento");
        MonitoringRule rule = find(code);
        if (!"INACTIVE".equals(rule.getStatus()) && !"DRAFT".equals(rule.getStatus())) {
            throw new BusinessException("Reativacao so se aplica a regra INACTIVE/DRAFT (status " + rule.getStatus() + ")");
        }
        Map<String, Object> before = state(rule);
        rule.setStatus("PENDING_APPROVAL");
        rule.setActive(false);
        rule.setCreatedBy(currentUser.user().orElse(null));
        rule.setEffectiveTo(null);
        rule = ruleRepository.save(rule);
        Map<String, Object> after = state(rule);
        after.put("changeReason", just);
        AuditPayload.log(auditService, "RULE_ACTIVATION_REQUESTED", "MONITORING_RULE", rule.getId(), before, after);
        return toResponse(rule);
    }

    @Transactional(readOnly = true)
    public List<RuleVersionResponse> versions(String code) {
        MonitoringRule rule = find(code);
        return versionRepository.findByRuleIdOrderByVersionDesc(rule.getId()).stream()
                .map(this::toVersionResponse).toList();
    }

    /** Versao da regra vigente em um instante (reconstrucao historica); vazio se a regra nao operava. */
    @Transactional(readOnly = true)
    public java.util.Optional<RuleVersionResponse> versionEffectiveAt(String code, Instant at) {
        MonitoringRule rule = find(code);
        return versionRepository.findEffectiveAt(rule.getId(), at).stream().findFirst()
                .map(this::toVersionResponse);
    }

    /**
     * Provisionamento de tenant: copia o catalogo global de regras-modelo para o tenant corrente, com a versao 1
     * (snapshot) de cada regra. Regras PENDING_APPROVAL continuam exigindo aprovacao de quatro olhos no tenant.
     * Idempotente (pula codigos ja existentes).
     */
    @Transactional
    @CacheEvict(value = "rules", allEntries = true)
    public int provisionFromTemplates() {
        Instant now = Instant.now();
        int copied = 0;
        for (MonitoringRuleTemplate t : templateRepository.findAllByOrderByCodeAsc()) {
            if (ruleRepository.findByCode(t.getCode()).isPresent()) {
                continue;
            }
            MonitoringRule r = new MonitoringRule();
            r.setCode(t.getCode());
            r.setName(t.getName());
            r.setDescription(t.getDescription());
            r.setCategory(t.getCategory());
            r.setSeverity(t.getSeverity());
            r.setActive(t.isActive());
            r.setStatus(t.getStatus());
            r.setConditionExpression(t.getConditionExpression());
            r.setThresholdValue(t.getThresholdValue());
            r.setThresholdPeriodDays(t.getThresholdPeriodDays());
            r.setParameters(t.getParameters());
            r.setCarta4001Reference(t.getCarta4001Reference());
            r.setAppliesToRiskLevels(t.getAppliesToRiskLevels());
            r.setAutoCommunication(t.isAutoCommunication());
            r.setVersion(1);
            if ("ACTIVE".equals(t.getStatus())) {
                r.setEffectiveFrom(now);
            }
            r = ruleRepository.save(r);
            versionRepository.save(MonitoringRuleVersion.builder()
                    .rule(r)
                    .version(1)
                    .snapshot(state(r))
                    .changeReason("Regra-modelo da plataforma (provisionamento do tenant)")
                    .effectiveFrom(r.getEffectiveFrom())
                    .build());
            copied++;
        }
        return copied;
    }

    // ------------------------------------------------------------------ internos

    private MonitoringRule find(String code) {
        return ruleRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + code));
    }

    /** Fecha a vigencia das versoes abertas (exceto {@code keepVersion}). */
    private void closeOpenVersions(MonitoringRule rule, Instant at, Integer keepVersion) {
        for (MonitoringRuleVersion v : versionRepository.findByRuleIdOrderByVersionDesc(rule.getId())) {
            if (v.getEffectiveTo() == null && (keepVersion == null || v.getVersion() != keepVersion)) {
                v.setEffectiveTo(at);
                versionRepository.save(v);
            }
        }
    }

    private Map<String, Object> state(MonitoringRule r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", r.getCode());
        m.put("name", r.getName());
        m.put("description", r.getDescription());
        m.put("category", r.getCategory());
        m.put("severity", r.getSeverity());
        m.put("status", r.getStatus());
        m.put("active", r.isActive());
        m.put("version", r.getVersion());
        m.put("thresholdValue", r.getThresholdValue());
        m.put("thresholdPeriodDays", r.getThresholdPeriodDays());
        m.put("parameters", r.getParameters());
        m.put("appliesToRiskLevels", r.getAppliesToRiskLevels());
        m.put("carta4001Reference", r.getCarta4001Reference());
        m.put("autoCommunication", r.isAutoCommunication());
        return m;
    }

    private RuleVersionResponse toVersionResponse(MonitoringRuleVersion v) {
        return new RuleVersionResponse(v.getVersion(), v.getSnapshot(), v.getChangeReason(),
                v.getApprovedBy() != null ? v.getApprovedBy().getId() : null,
                v.getApprovedAt(), v.getEffectiveFrom(), v.getEffectiveTo(),
                v.getCreatedBy() != null ? v.getCreatedBy().getId() : null,
                v.getCreatedBy() != null ? v.getCreatedBy().getName() : null,
                v.getApprovedBy() != null ? v.getApprovedBy().getName() : null);
    }

    /** Mapeamento completo (governanca incluida); deve ser chamado dentro de transacao (nomes sao lazy). */
    public MonitoringRuleResponse toResponse(MonitoringRule rule) {
        return MonitoringRuleResponse.builder()
                .id(rule.getId())
                .code(rule.getCode())
                .name(rule.getName())
                .description(rule.getDescription())
                .category(rule.getCategory())
                .severity(rule.getSeverity())
                .active(rule.isActive())
                .status(rule.getStatus())
                .version(rule.getVersion())
                .parameters(rule.getParameters())
                .effectiveFrom(rule.getEffectiveFrom())
                .effectiveTo(rule.getEffectiveTo())
                .thresholdValue(rule.getThresholdValue())
                .thresholdPeriodDays(rule.getThresholdPeriodDays())
                .createdById(rule.getCreatedBy() != null ? rule.getCreatedBy().getId() : null)
                .createdByName(rule.getCreatedBy() != null ? rule.getCreatedBy().getName() : null)
                .approvedById(rule.getApprovedBy() != null ? rule.getApprovedBy().getId() : null)
                .approvedByName(rule.getApprovedBy() != null ? rule.getApprovedBy().getName() : null)
                .approvedAt(rule.getApprovedAt())
                .carta4001Reference(rule.getCarta4001Reference())
                .appliesToRiskLevels(rule.getAppliesToRiskLevels() != null
                        ? new ArrayList<>(rule.getAppliesToRiskLevels()) : null)
                .autoCommunication(rule.isAutoCommunication())
                .build();
    }

    /** Usado por testes e controladores: identificador da regra por codigo. */
    public UUID idOf(String code) {
        return find(code).getId();
    }
}
