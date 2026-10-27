package com.hireflow.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * HireFlow REST API Spring Boot Application Entry Point (Stage 4A).
 * <p>
 * Key Architectural Constraints:
 * <ul>
 *     <li>Component scan is strictly bounded to {@code com.hireflow.api}.</li>
 *     <li>Launches independently from the Swing desktop application and Tomcat WAR.</li>
 *     <li>Does not instantiate Swing UI components or legacy TCP sockets.</li>
 *     <li>Operates against Flyway-managed PostgreSQL schemas with Hibernate validation-only mode.</li>
 *     <li>Fails fast if configured PostgreSQL database is unreachable.</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {"com.hireflow.api"})
public class HireFlowApiApplication {

    private static final Logger log = LoggerFactory.getLogger(HireFlowApiApplication.class);

    public static void main(String[] args) {
        log.info("Starting HireFlow REST API Application (Stage 4A Spring Boot Foundation)...");
        SpringApplication.run(HireFlowApiApplication.class, args);
    }
}
