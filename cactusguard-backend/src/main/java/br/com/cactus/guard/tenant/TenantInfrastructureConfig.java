package br.com.cactus.guard.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.UUID;

/** Liga o tenant ao Hibernate, ao pool de conexoes (RLS) e ao cache. */
@Configuration
public class TenantInfrastructureConfig {

    /** Somente perfil de teste: tenant usado quando a thread nao tem nenhum (testes legados rodam no "default"). */
    @Bean
    TenantFallbackInitializer tenantFallbackInitializer(@Value("${cactus.tenant.fallback-id:}") String fallbackId) {
        return new TenantFallbackInitializer(fallbackId);
    }

    @Bean
    HibernatePropertiesCustomizer tenantHibernatePropertiesCustomizer(TenantIdentifierResolver resolver) {
        return props -> props.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }

    /** Envolve o DataSource principal (nao o de outras origens) com o aplicador do GUC de tenant. */
    @Bean
    static BeanPostProcessor tenantDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if ("dataSource".equals(beanName) && bean instanceof DataSource ds && !(bean instanceof TenantAwareDataSource)) {
                    return new TenantAwareDataSource(ds);
                }
                return bean;
            }
        };
    }

    /** Envolve qualquer CacheManager (Redis em dev/prod, simple nos testes) com o namespace por tenant. */
    @Bean
    static BeanPostProcessor tenantCacheManagerPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof CacheManager cm && !(bean instanceof TenantAwareCacheManager)) {
                    return new TenantAwareCacheManager(cm);
                }
                return bean;
            }
        };
    }

    public static final class TenantFallbackInitializer {
        TenantFallbackInitializer(String fallbackId) {
            TenantContext.configureFallback(
                    fallbackId == null || fallbackId.isBlank() ? null : UUID.fromString(fallbackId.trim()));
        }
    }
}
