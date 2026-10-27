package com.hireflow.api.config;

import com.hireflow.service.EligibilityService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration bridging legacy HireFlow domain services into the Spring application context.
 * <p>
 * Key Architectural Guarantees:
 * <ul>
 *     <li>Zero duplication of business rules: existing algorithms (such as {@link EligibilityService})
 *         are reused directly from the compiled core classes.</li>
 *     <li>Decoupled transaction boundaries: Spring-managed {@code JpaTransactionManager} operates
 *         independently from the legacy {@code TransactionManager}. No write transactions or Hibernate mutations
 *         are executed alongside JDBC mutations in Stage 4A.</li>
 *     <li>Pool and schema isolation: Spring Boot initializes its own dedicated HikariCP pool
 *         and performs schema validation against Flyway V1-V3 tables without attempting schema modifications.</li>
 * </ul>
 */
@Configuration
public class LegacyServiceBridgeConfig {

    /**
     * Exposes the canonical, zero-dependency {@link EligibilityService} as a Spring-managed singleton.
     */
    @Bean
    public EligibilityService eligibilityService() {
        return new EligibilityService();
    }
}
