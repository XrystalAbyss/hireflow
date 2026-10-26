package com.hireflow.api.testsupport;

import java.sql.DriverManager;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Runs before application beans, Flyway, Hibernate or fixture setup can write. */
public final class IsolatedDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override public void initialize(ConfigurableApplicationContext context) {
        var env=context.getEnvironment();
        if (!"55432/hireflow_cap8_it".equals(System.getenv("HIREFLOW_ALLOW_ISOLATED_IT")))
            throw new IllegalStateException("Explicit isolated database test opt-in is required");
        String url=env.getProperty("spring.datasource.url");
        String user=env.getProperty("spring.datasource.username");
        SafeTestDatabase.requireTarget(url,user);
        String coreUrl=env.getProperty("hireflow.db.url",url);
        String coreUser=env.getProperty("hireflow.db.user",user);
        SafeTestDatabase.requireTarget(coreUrl,coreUser);
        try(var c=DriverManager.getConnection(url,user,env.getProperty("spring.datasource.password"))) {
            SafeTestDatabase.captureBaseline(c);
        } catch(Exception e) { throw new IllegalStateException("Isolated test database preflight failed",e); }
    }
}
