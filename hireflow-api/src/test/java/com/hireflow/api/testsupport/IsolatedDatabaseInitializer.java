package com.hireflow.api.testsupport;

import java.sql.DriverManager;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Runs before application beans, Flyway, Hibernate or fixture setup can write. */
public final class IsolatedDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override public void initialize(ConfigurableApplicationContext context) {
        var env=context.getEnvironment();
        String allow = System.getenv("HIREFLOW_ALLOW_ISOLATED_IT");
        if (allow == null || allow.isBlank()) {
            allow = System.getProperty("HIREFLOW_ALLOW_ISOLATED_IT");
        }
        if (allow == null || allow.isBlank()) {
            allow = env.getProperty("HIREFLOW_ALLOW_ISOLATED_IT");
        }
        if (!"55432/hireflow_cap8_it".equals(allow))
            throw new IllegalStateException("Explicit isolated database test opt-in is required");
        String url=env.getProperty("spring.datasource.url");
        String user=env.getProperty("spring.datasource.username");
        SafeTestDatabase.requireTarget(url,user);
        String coreUrl=env.getProperty("hireflow.db.url",url);
        String coreUser=env.getProperty("hireflow.db.user",user);
        SafeTestDatabase.requireTarget(coreUrl,coreUser);
        String pass = env.getProperty("spring.datasource.password");
        if (pass == null || pass.isBlank()) {
            pass = env.getProperty("hireflow.db.password");
        }
        if (pass == null || pass.isBlank()) {
            pass = System.getProperty("hireflow.it.pass");
        }
        if (pass == null || pass.isBlank()) {
            pass = System.getenv("HIREFLOW_IT_APP_PASSWORD");
        }
        try(var c=DriverManager.getConnection(url,user,pass)) {
            SafeTestDatabase.captureBaseline(c);
            com.hireflow.policy.UniversityCampusRoomPolicy.setTestContextRegistrationAllowed(true);
        } catch(Exception e) { throw new IllegalStateException("Isolated test database preflight failed",e); }
    }
}
