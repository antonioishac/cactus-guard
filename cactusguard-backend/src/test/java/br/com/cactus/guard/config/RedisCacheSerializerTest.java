package br.com.cactus.guard.config;

import br.com.cactus.guard.rule.dto.MonitoringRuleResponse;
import br.com.cactus.guard.rule.entity.MonitoringRule;
import br.com.cactus.guard.rule.repository.MonitoringRuleRepository;
import br.com.cactus.guard.rule.service.MonitoringRuleService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Garante que os valores que vao para o cache Redis fazem round-trip pelo serializer configurado
 * em {@link RedisCacheConfig}. Usa o retorno real de {@link MonitoringRuleService#findAllActive()}
 * (mesmo tipo de lista e DTO) sem precisar de Redis: so exercita a (de)serializacao.
 */
class RedisCacheSerializerTest {

    @Test
    void cachedActiveRulesShouldRoundTrip() {
        MonitoringRule rule = new MonitoringRule();
        rule.setId(UUID.randomUUID());
        rule.setCode("TXN_HIGH_VALUE");
        rule.setName("Transação de alto valor");
        rule.setCategory("TRANSACTIONAL");
        rule.setSeverity("HIGH");
        rule.setActive(true);
        rule.setThresholdValue(new BigDecimal("50000.00"));
        rule.setThresholdPeriodDays(30);

        MonitoringRuleRepository repository = mock(MonitoringRuleRepository.class);
        when(repository.findByActiveTrue()).thenReturn(List.of(rule));
        List<MonitoringRuleResponse> cached = new MonitoringRuleService(repository).findAllActive();

        var serializer = RedisCacheConfig.cacheValueSerializer();
        Object restored = serializer.deserialize(serializer.serialize(cached));

        assertEquals(cached, restored);
    }

    @Test
    void cachedNullShouldRoundTrip() {
        var serializer = RedisCacheConfig.cacheValueSerializer();
        assertNull(serializer.deserialize(serializer.serialize(null)));
    }
}
