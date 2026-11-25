package com.arrheniq.kernel.tenancy;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Replaces Spring Boot's default JPA transaction manager with the tenant-aware one.
 * The bean is named {@code transactionManager} so {@code @Transactional} uses it everywhere.
 */
@Configuration(proxyBeanMethods = false)
public class TenancyConfiguration {

    @Bean(name = "transactionManager")
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new TenantAwareJpaTransactionManager(entityManagerFactory);
    }
}
