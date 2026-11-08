package br.com.cactus.guard.rule.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

import br.com.cactus.guard.rule.dto.MonitoringRuleResponse;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MonitoringRuleService {

    private final MonitoringRuleRepository ruleRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = "rules", key = "'active'")
    public List<MonitoringRuleResponse> findAllActive() {
        // ArrayList (nao Stream#toList): a lista imutavel do JDK e final e sai sem type id no
        // JSON polimorfico do cache Redis, o que impede a desserializacao
        return ruleRepository.findByActiveTrue().stream()
                .map(this::toResponse)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "rules", key = "'category:' + #category")
    public List<MonitoringRuleResponse> findByCategory(String category) {
        // ArrayList (nao Stream#toList): a lista imutavel do JDK e final e sai sem type id no
        // JSON polimorfico do cache Redis, o que impede a desserializacao
        return ruleRepository.findByCategoryAndActiveTrue(category).stream()
                .map(this::toResponse)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "rules", key = "'active'"),
            @CacheEvict(value = "rules", allEntries = true)
    })
    public MonitoringRuleResponse toggleActive(String code, boolean active) {
        MonitoringRule rule = ruleRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + code));
        rule.setActive(active);
        rule = ruleRepository.save(rule);
        return toResponse(rule);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "rules", key = "'active'"),
            @CacheEvict(value = "rules", allEntries = true)
    })
    public MonitoringRuleResponse create(MonitoringRule rule) {
        rule = ruleRepository.save(rule);
        return toResponse(rule);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "rules", key = "'active'"),
            @CacheEvict(value = "rules", allEntries = true)
    })
    public MonitoringRuleResponse update(String code, MonitoringRule updated) {
        MonitoringRule rule = ruleRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + code));
        rule.setName(updated.getName());
        rule.setDescription(updated.getDescription());
        rule.setSeverity(updated.getSeverity());
        rule.setThresholdValue(updated.getThresholdValue());
        rule.setThresholdPeriodDays(updated.getThresholdPeriodDays());
        rule = ruleRepository.save(rule);
        return toResponse(rule);
    }

    private MonitoringRuleResponse toResponse(MonitoringRule rule) {
        return MonitoringRuleResponse.builder()
                .id(rule.getId())
                .code(rule.getCode())
                .name(rule.getName())
                .description(rule.getDescription())
                .category(rule.getCategory())
                .severity(rule.getSeverity())
                .active(rule.isActive())
                .thresholdValue(rule.getThresholdValue())
                .thresholdPeriodDays(rule.getThresholdPeriodDays())
                .build();
    }
}
