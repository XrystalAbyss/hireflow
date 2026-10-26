package com.hireflow.infra;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

public class MigrateDatabase {
    public static void main(String[] args) {
        String url = System.getProperty("hireflow.it.url", "jdbc:postgresql://127.0.0.1:55432/hireflow_cap8_it");
        String user = System.getProperty("hireflow.it.user", "hireflow_cap8_app");
        String pass = System.getProperty("hireflow.it.pass");
        if (pass == null || pass.isBlank()) {
            pass = System.getenv("HIREFLOW_IT_APP_PASSWORD");
        }
        if (pass == null || pass.isBlank()) {
            throw new IllegalStateException("Database password not provided. Set HIREFLOW_IT_APP_PASSWORD or pass -Dhireflow.it.pass");
        }

        System.out.println("[FlywayRunner] Connecting to isolated database: " + url + " as user: " + user);
        Flyway flyway = Flyway.configure()
                .dataSource(url, user, pass)
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .load();

        MigrateResult result = flyway.migrate();
        System.out.println("[FlywayRunner] Migration Success: " + result.success);
        System.out.println("[FlywayRunner] Executed Migrations: " + result.migrationsExecuted);
        System.out.println("[FlywayRunner] Target Schema Version: " + result.targetSchemaVersion);
    }
}
