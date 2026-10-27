package com.hireflow.api.config;

import com.hireflow.api.HireFlowApiApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseFailureStartupTest {

    @Test
    @DisplayName("Application startup fails fast and clearly when PostgreSQL database is unreachable")
    void startupFailsFastWhenDatabaseUnreachable() {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(HireFlowApiApplication.class);

        Exception exception = assertThrows(Exception.class, () -> {
            ConfigurableApplicationContext ctx = builder.run(
                    "--server.port=0",
                    "--spring.main.banner-mode=off",
                    "--hireflow.db.url=jdbc:postgresql://127.0.0.1:59999/unreachable_hireflow_db",
                    "--spring.datasource.url=jdbc:postgresql://127.0.0.1:59999/unreachable_hireflow_db",
                    "--spring.datasource.username=invalid_user",
                    "--spring.datasource.password=invalid_pass",
                    "--spring.datasource.hikari.connection-timeout=1000",
                    "--spring.datasource.hikari.initialization-fail-timeout=1",
                    "--spring.flyway.enabled=true",
                    "--spring.flyway.connect-retries=0",
                    "--spring.jpa.hibernate.ddl-auto=validate"
            );
            ctx.close();
        });

        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        String combined = (exception.getMessage() + " " + rootCause.getMessage()).toLowerCase();

        boolean hasExpectedCause = combined.contains("connection") ||
                combined.contains("refused") ||
                combined.contains("hikari") ||
                combined.contains("flyway") ||
                combined.contains("socket") ||
                combined.contains("database");

        assertTrue(hasExpectedCause,
                "Startup exception must clearly indicate database connection failure. Actual: " + rootCause.getMessage());
    }
}
