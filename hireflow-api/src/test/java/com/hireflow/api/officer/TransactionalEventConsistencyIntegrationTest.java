package com.hireflow.api.officer;

import com.hireflow.db.TransactionManager;
import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.*;
import com.hireflow.repository.*;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.EligibilityService;
import com.hireflow.service.event.HireFlowEvent;
import com.hireflow.service.event.HireFlowEventListener;
import com.hireflow.service.event.HireFlowEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("TransactionalEventConsistencyIntegrationTest - PostgreSQL Commit and Event Lifecycle Integrity")
class TransactionalEventConsistencyIntegrationTest {

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private DriveRepository driveRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    @Qualifier("hireflowJdbcTransactionManager")
    private TransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    private static final String CMP_ID = "CMP-EVT-01";
    private static final String DRV_ID = "DRV-EVT-01";
    private static final String STU_ID = "STU-EVT-01";
    private static final String APP_ID = "APP-EVT-01";

    private final List<HireFlowEvent> recordedEvents = new CopyOnWriteArrayList<>();
    private final HireFlowEventListener testListener = recordedEvents::add;

    @BeforeEach
    void setUpTestData() {
        cleanTestData();
        applicationService.addEventListener(testListener);

        companyRepository.add(new Company(CMP_ID, "Event Test Corp", "Technology"));
        driveRepository.add(new RecruitmentDrive(
                DRV_ID, CMP_ID, "Event Systems Engineer", 7.0, Set.of("CSE", "ECE"), LocalDate.now().plusMonths(1), false
        ));
        studentRepository.add(new Student(
                STU_ID, "Event Candidate", "evt@hireflow.test", "CSE", 8.5, 2026, true
        ));

        Application app = new Application(
                APP_ID, STU_ID, DRV_ID, LocalDateTime.now(), ApplicationStatus.APPLIED, "Initial applied status"
        );
        applicationRepository.add(app);
        recordedEvents.clear();
    }

    @AfterEach
    void tearDown() {
        applicationService.removeEventListener(testListener);
        cleanTestData();
    }

    private void cleanTestData() {
        try {
            applicationRepository.findByDriveId(DRV_ID).forEach(a -> applicationRepository.deleteById(a.getId()));
            studentRepository.deleteById(STU_ID);
            studentRepository.deleteById("STU-EVT-02");
            studentRepository.deleteById("STU-EVT-03");
            driveRepository.deleteById(DRV_ID);
            companyRepository.deleteById(CMP_ID);
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("1. Successful shortlisting commits and emits exactly one expected event after commit")
    void testSuccessfulShortlisting_emitsExactlyOneEvent_afterCommit() {
        Application result = transactionManager.runInTransaction(conn ->
                applicationService.shortlistApplication(APP_ID, false)
        );

        assertNotNull(result);
        assertEquals(ApplicationStatus.SHORTLISTED, result.getStatus());

        // Verify event was emitted post-commit
        assertEquals(1, recordedEvents.size(), "Exactly 1 event must be emitted on successful commit.");
        HireFlowEvent event = recordedEvents.get(0);
        assertEquals(HireFlowEventType.APPLICATION_SHORTLISTED, event.type());
        assertEquals(APP_ID, event.sourceId());
    }

    @Test
    @DisplayName("2. Duplicate shortlisting returns conflict or legacy idempotent behavior without duplicate success event")
    void testDuplicateShortlisting_doesNotEmitDuplicateEvent() {
        // First shortlisting succeeds
        transactionManager.runInTransaction(conn ->
                applicationService.shortlistApplication(APP_ID, false)
        );
        assertEquals(1, recordedEvents.size());
        recordedEvents.clear();

        // Repeat shortlisting in REST mode (strict): throws DuplicateEntityException
        assertThrows(DuplicateEntityException.class, () ->
                transactionManager.runInTransaction(conn ->
                        applicationService.shortlistApplication(APP_ID, false)
                )
        );
        assertEquals(0, recordedEvents.size(), "Duplicate shortlisting in strict REST mode must NOT emit an event.");

        // Repeat shortlisting in JSP mode (allowAlreadyShortlisted = true): idempotent no-op
        Application jspResult = transactionManager.runInTransaction(conn ->
                applicationService.shortlistApplication(APP_ID, true)
        );
        assertEquals(ApplicationStatus.SHORTLISTED, jspResult.getStatus());
        assertEquals(0, recordedEvents.size(), "Idempotent shortlisting must NOT emit a duplicate event.");
    }

    @Test
    @DisplayName("3. Conditional update affecting zero rows (concurrency race) does not emit a false success event")
    void testZeroRowsAffected_doesNotEmitFalseEvent() throws SQLException {
        // Concurrently change status in database directly behind the service's back
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE applications SET status = 'SHORTLISTED' WHERE id = ?")) {
            ps.setString(1, APP_ID);
            ps.executeUpdate();
        }

        // Service attempts shortlisting -> affected rows will be 0 -> triggers race detection
        assertThrows(DuplicateEntityException.class, () ->
                transactionManager.runInTransaction(conn ->
                        applicationService.shortlistApplication(APP_ID, false)
                )
        );

        assertEquals(0, recordedEvents.size(), "Zero rows affected must not emit any success event.");
    }

    @Test
    @DisplayName("4. Explicit transaction rollback suppresses pending events")
    void testExplicitRollback_suppressesPendingEvents() {
        assertThrows(RuntimeException.class, () ->
                transactionManager.runInTransaction(conn -> {
                    applicationService.shortlistApplication(APP_ID, false);
                    // Explicit rollback via exception before commit
                    throw new RuntimeException("Simulated mid-transaction failure");
                })
        );

        // Verify status in PostgreSQL rolled back to APPLIED
        Application app = applicationRepository.findById(APP_ID).orElseThrow();
        assertEquals(ApplicationStatus.APPLIED, app.getStatus());

        // Crucial invariant: zero events delivered
        assertEquals(0, recordedEvents.size(), "Rolled back transaction must NOT emit any event.");
    }

    @Test
    @DisplayName("5. An exception before commit suppresses pending events")
    void testExceptionBeforeCommit_suppressesPendingEvents() {
        assertThrows(ValidationException.class, () ->
                transactionManager.runInTransaction(conn -> {
                    applicationService.shortlistApplication(APP_ID, false);
                    // Simulated secondary validation error before transaction completes
                    throw new ValidationException("Secondary business constraint violated.");
                })
        );

        Application app = applicationRepository.findById(APP_ID).orElseThrow();
        assertEquals(ApplicationStatus.APPLIED, app.getStatus());
        assertEquals(0, recordedEvents.size(), "Exception before commit must suppress all staged events.");
    }

    @Test
    @DisplayName("6. A real database commit failure suppresses pending success events")
    void testCommitFailure_suppressesPendingEvents() {
        // Construct a transaction where physicalConn.commit() fails
        DataSource faultyDataSource = new javax.sql.DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                Connection realConn = dataSource.getConnection();
                // Return a proxy that fails on commit()
                return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                        Connection.class.getClassLoader(),
                        new Class<?>[]{Connection.class},
                        (proxy, method, args) -> {
                            if ("commit".equals(method.getName())) {
                                throw new SQLException("Simulated PostgreSQL commit failure (e.g. disk full / lost connection).", "08006");
                            }
                            return method.invoke(realConn, args);
                        }
                );
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return getConnection();
            }

            @Override
            public java.io.PrintWriter getLogWriter() { return null; }
            @Override
            public void setLogWriter(java.io.PrintWriter out) {}
            @Override
            public void setLoginTimeout(int seconds) {}
            @Override
            public int getLoginTimeout() { return 0; }
            @Override
            public java.util.logging.Logger getParentLogger() { return null; }
            @Override
            public <T> T unwrap(Class<T> iface) { return null; }
            @Override
            public boolean isWrapperFor(Class<?> iface) { return false; }
        };

        TransactionManager faultyTxManager = new TransactionManager(faultyDataSource, 0);

        assertThrows(Exception.class, () ->
                faultyTxManager.runInTransaction(conn ->
                        applicationService.shortlistApplication(APP_ID, false)
                )
        );

        assertEquals(0, recordedEvents.size(), "Commit failure must NOT emit any staged post-commit events.");
    }

    @Test
    @DisplayName("7. REST and JSP service entry points follow the same transactional event policy")
    void testRestAndJspEntryPoints_followSameEventPolicy() {
        // 1. JSP call inside transaction
        transactionManager.runInTransaction(conn ->
                applicationService.shortlistApplication(APP_ID, true)
        );
        assertEquals(1, recordedEvents.size());
        assertEquals(HireFlowEventType.APPLICATION_SHORTLISTED, recordedEvents.get(0).type());
        recordedEvents.clear();

        // 2. Rollback with JSP entry point
        studentRepository.add(new Student("STU-EVT-02", "JSP Candidate", "jsp@hireflow.test", "CSE", 8.5, 2026, true));
        Application app2 = new Application("APP-EVT-02", "STU-EVT-02", DRV_ID, LocalDateTime.now(), ApplicationStatus.APPLIED, "JSP App");
        applicationRepository.add(app2);

        assertThrows(RuntimeException.class, () ->
                transactionManager.runInTransaction(conn -> {
                    applicationService.shortlistApplication("APP-EVT-02", true);
                    throw new RuntimeException("Force rollback in JSP path");
                })
        );
        assertEquals(0, recordedEvents.size(), "Rolled back JSP shortlisting must NOT emit events.");
        applicationRepository.deleteById("APP-EVT-02");
        studentRepository.deleteById("STU-EVT-02");
    }

    @Test
    @DisplayName("8. Event callbacks cannot cause nested transaction corruption")
    void testEventCallback_cannotCauseNestedTransactionCorruption() {
        AtomicBoolean callbackExecuted = new AtomicBoolean(false);

        HireFlowEventListener dbQueryListener = event -> {
            callbackExecuted.set(true);
            // In the event callback, transaction must be inactive, so starting a new transaction or reading is safe
            assertFalse(TransactionManager.isCurrentTransactionActive(),
                    "Transaction MUST be inactive and already committed when event callback executes.");
            Application app = applicationRepository.findById(event.sourceId()).orElse(null);
            assertNotNull(app);
            assertEquals(ApplicationStatus.SHORTLISTED, app.getStatus());
        };

        applicationService.addEventListener(dbQueryListener);
        try {
            transactionManager.runInTransaction(conn ->
                    applicationService.shortlistApplication(APP_ID, false)
            );
            assertTrue(callbackExecuted.get());
        } finally {
            applicationService.removeEventListener(dbQueryListener);
        }
    }

    @Test
    @DisplayName("9. Event callback failure does not falsely roll back an already committed transaction")
    void testEventCallbackFailure_doesNotRollbackCommittedTransaction() {
        HireFlowEventListener explodingListener = event -> {
            throw new RuntimeException("Simulated external listener crash (e.g. socket timeout, notification error)");
        };

        applicationService.addEventListener(explodingListener);
        try {
            // Execution should succeed despite listener failure
            assertDoesNotThrow(() ->
                    transactionManager.runInTransaction(conn ->
                            applicationService.shortlistApplication(APP_ID, false)
                    )
            );

            // Crucial database guarantee: Application was committed to PostgreSQL!
            Application app = applicationRepository.findById(APP_ID).orElseThrow();
            assertEquals(ApplicationStatus.SHORTLISTED, app.getStatus(),
                    "Post-commit listener failure must NEVER roll back an already committed database transaction.");
        } finally {
            applicationService.removeEventListener(explodingListener);
        }
    }

    @Test
    @DisplayName("10. Event resources and ThreadLocals are cleaned up after rollback and commit")
    void testEventResourcesCleanedUp_afterRollbackAndCommit() {
        // 1. Verify clean state after commit
        transactionManager.runInTransaction(conn ->
                applicationService.shortlistApplication(APP_ID, false)
        );
        assertFalse(TransactionManager.isCurrentTransactionActive());

        // 2. Verify clean state after rollback
        studentRepository.add(new Student("STU-EVT-03", "Clean Candidate", "clean@hireflow.test", "CSE", 8.5, 2026, true));
        Application app3 = new Application("APP-EVT-03", "STU-EVT-03", DRV_ID, LocalDateTime.now(), ApplicationStatus.APPLIED, "Clean App");
        applicationRepository.add(app3);

        assertThrows(RuntimeException.class, () ->
                transactionManager.runInTransaction(conn -> {
                    applicationService.shortlistApplication("APP-EVT-03", false);
                    throw new RuntimeException("Rollback cleanup test");
                })
        );
        assertFalse(TransactionManager.isCurrentTransactionActive());
        applicationRepository.deleteById("APP-EVT-03");
        studentRepository.deleteById("STU-EVT-03");
    }
}
