package com.hireflow.testsupport;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import java.util.Set;

/**
 * Fail closed before any core test class can connect, migrate or seed fixtures
 * against an unauthorized database environment.
 * Pure unit tests and in-memory suites are excluded so offline verification
 * and unit testing execute cleanly without requiring Docker/PostgreSQL.
 */
public final class CoreDatabaseExtension implements BeforeAllCallback, AfterAllCallback {

    private static final Set<String> POSTGRES_TEST_CLASSES = Set.of(
            "Stage2FinalAcceptanceTest",
            "ApplicationContextLifecycleTest",
            "JdbcDevRestartVerification",
            "JdbcPersistenceIntegrationTest",
            "DatabaseInfrastructureTest",
            "FlywayAndConcurrencyTest",
            "JdbcSchedulingPublicationConcurrencyTest",
            "HireFlowEventBroadcastingTest",
            "HireFlowTcpServerTest",
            "JdbcApplicationRepositoryTest",
            "JdbcCompanyRoomStudentRepositoryTest",
            "JdbcInterviewSessionRepositoryTest",
            "JdbcInterviewerDriveRepositoryTest",
            "NetworkingPanelIntegrationTest",
            "TomcatPostgresE2EWorkflowTest",
            "WebContextListenerTest",
            "JdbcUserAccountRepositoryTest"
    );

    private boolean isDatabaseTest(ExtensionContext context) {
        return context.getTestClass()
                .map(cls -> POSTGRES_TEST_CLASSES.contains(cls.getSimpleName())
                        || context.getTags().contains("requires-postgres")
                        || context.getTags().contains("postgres"))
                .orElse(false);
    }

    private boolean topLevel(ExtensionContext context) {
        return context.getRequiredTestClass().getEnclosingClass() == null;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (topLevel(context) && isDatabaseTest(context)) {
            CoreTestDatabase.beforeClass();
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (topLevel(context) && isDatabaseTest(context)) {
            CoreTestDatabase.afterClass();
        }
    }
}
