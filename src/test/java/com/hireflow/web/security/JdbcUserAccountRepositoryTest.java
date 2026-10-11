package com.hireflow.web.security;

import com.hireflow.db.DatabaseConfig;
import com.hireflow.db.DatabaseMode;
import com.hireflow.db.DataSourceProvider;
import com.hireflow.db.FlywayMigrationService;
import com.hireflow.db.TransactionManager;
import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.model.Student;
import com.hireflow.repository.JdbcStudentRepository;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("JdbcUserAccountRepository - PostgreSQL V2 Schema & Constraints Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JdbcUserAccountRepositoryTest {

    private static DatabaseConfig testConfig;
    private static DataSourceProvider dataSourceProvider;
    private static TransactionManager transactionManager;
    private static boolean dbAvailable = false;

    private JdbcUserAccountRepository userRepo;
    private JdbcStudentRepository studentRepo;

    private static final String TEST_STUDENT_ID = "TEST-STU-3C-001";

    @BeforeAll
    static void setUpAll() {
        Properties localProps = new Properties();
        File localFile = new File("hireflow-local.properties");
        if (localFile.exists()) {
            try (FileInputStream fis = new FileInputStream(localFile)) {
                localProps.load(fis);
            } catch (Exception ignored) {
            }
        }

        String url = System.getenv().getOrDefault("HIREFLOW_DB_URL",
                System.getProperty("hireflow.db.url", localProps.getProperty("hireflow.db.url", "jdbc:postgresql://localhost:5432/hireflow_test")));
        String user = System.getenv().getOrDefault("HIREFLOW_DB_USER",
                System.getProperty("hireflow.db.user", localProps.getProperty("hireflow.db.user", "hireflow_user")));
        String pass = System.getenv().getOrDefault("HIREFLOW_DB_PASSWORD",
                System.getProperty("hireflow.db.password", localProps.getProperty("hireflow.db.password", "")));

        testConfig = DatabaseConfig.builder()
                .mode(DatabaseMode.JDBC_TEST)
                .jdbcUrl(url)
                .username(user)
                .password(pass)
                .maximumPoolSize(5)
                .minimumIdle(1)
                .connectionTimeoutMs(5000)
                .build();

        try {
            dataSourceProvider = new DataSourceProvider(testConfig);
            dbAvailable = dataSourceProvider.verifyConnectionHealth(3);
            if (dbAvailable) {
                FlywayMigrationService migrationService = new FlywayMigrationService(dataSourceProvider.getDataSource());
                migrationService.migrate();
                transactionManager = new TransactionManager(dataSourceProvider.getDataSource(), 3);
            }
        } catch (Exception ex) {
            dbAvailable = false;
        }
    }

    @AfterAll
    static void tearDownAll() {
        if (dataSourceProvider != null && !dataSourceProvider.isClosed()) {
            dataSourceProvider.close();
        }
    }

    @BeforeEach
    void setUp() {
        assumeTrue(dbAvailable, "PostgreSQL test database not available at localhost:5432/hireflow_test");
        userRepo = new JdbcUserAccountRepository(transactionManager);
        studentRepo = new JdbcStudentRepository(transactionManager);

        // Ensure prerequisite test student exists
        cleanTestData();
        studentRepo.add(new Student(TEST_STUDENT_ID, "Test Student 3C", "test3c@univ.edu", "CSE", 8.5, 2026, true));
    }

    @AfterEach
    void tearDown() {
        if (dbAvailable) {
            cleanTestData();
        }
    }

    private void cleanTestData() {
        try (Connection conn = dataSourceProvider.getDataSource().getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM user_accounts WHERE username LIKE 'test-%'")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM students WHERE id = ?")) {
                ps.setString(1, TEST_STUDENT_ID);
                ps.executeUpdate();
            }
        } catch (Exception ignored) {
        }
    }

    @Test
    @Order(1)
    @DisplayName("schema: V2 Flyway migration successfully applies user_accounts table")
    void testV2MigrationApplied() {
        assertNotNull(userRepo);
        assertTrue(userRepo.count() >= 0);
    }

    @Test
    @Order(2)
    @DisplayName("persistence: successfully saves and retrieves student user account with foreign key")
    void testSaveAndRetrieveStudentAccount() {
        String uid = "test-stu-" + UUID.randomUUID().toString().substring(0, 8);
        UserAccount account = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username(uid)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLM")
                .role(UserRole.STUDENT)
                .studentId(TEST_STUDENT_ID)
                .displayName("Student Tester")
                .build();

        userRepo.save(account);

        Optional<UserAccount> found = userRepo.findByUsername(uid);
        assertTrue(found.isPresent());
        assertEquals(uid, found.get().getUsername());
        assertEquals(TEST_STUDENT_ID, found.get().getStudentId());
        assertEquals(UserRole.STUDENT, found.get().getRole());
        assertTrue(found.get().isEnabled());
    }

    @Test
    @Order(3)
    @DisplayName("persistence: successfully saves and retrieves placement officer account with null studentId")
    void testSaveAndRetrieveOfficerAccount() {
        String uid = "test-officer-" + UUID.randomUUID().toString().substring(0, 8);
        UserAccount account = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username(uid)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLM")
                .role(UserRole.PLACEMENT_OFFICER)
                .studentId(null)
                .displayName("TPC Officer Tester")
                .build();

        userRepo.save(account);

        Optional<UserAccount> found = userRepo.findByUsername(uid);
        assertTrue(found.isPresent());
        assertNull(found.get().getStudentId());
        assertEquals(UserRole.PLACEMENT_OFFICER, found.get().getRole());
    }

    @Test
    @Order(4)
    @DisplayName("integrity: enforces unique username constraint")
    void testEnforcesUniqueUsername() {
        String uid = "test-unique-" + UUID.randomUUID().toString().substring(0, 8);
        UserAccount account1 = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username(uid)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLM")
                .role(UserRole.PLACEMENT_OFFICER)
                .studentId(null)
                .displayName("Officer A")
                .build();

        userRepo.save(account1);

        UserAccount account2 = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username(uid)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLM")
                .role(UserRole.PLACEMENT_OFFICER)
                .studentId(null)
                .displayName("Officer B")
                .build();

        assertThrows(DuplicateEntityException.class, () -> userRepo.save(account2));
    }

    @Test
    @Order(5)
    @DisplayName("integrity: enforces foreign key constraint to students table")
    void testEnforcesStudentForeignKey() {
        UserAccount account = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username("test-orphan-student")
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLM")
                .role(UserRole.STUDENT)
                .studentId("NON-EXISTENT-STUDENT-ID")
                .displayName("Orphan Student")
                .build();

        assertThrows(Exception.class, () -> userRepo.save(account));
    }

    @Test
    @Order(6)
    @DisplayName("state: updateLastLogin, recordFailedAttempt, and resetFailedAttempts")
    void testStateUpdates() {
        String uid = "test-state-" + UUID.randomUUID().toString().substring(0, 8);
        UserAccount account = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username(uid)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLM")
                .role(UserRole.PLACEMENT_OFFICER)
                .studentId(null)
                .displayName("Officer State")
                .build();

        userRepo.save(account);

        Instant lockTime = Instant.now().plusSeconds(300);
        userRepo.recordFailedAttempt(account.getId(), 3, lockTime);

        UserAccount updated = userRepo.findById(account.getId()).orElseThrow();
        assertEquals(3, updated.getFailedLoginAttempts());
        assertNotNull(updated.getLockedUntil());

        userRepo.resetFailedAttempts(account.getId());
        UserAccount reset = userRepo.findById(account.getId()).orElseThrow();
        assertEquals(0, reset.getFailedLoginAttempts());
        assertNull(reset.getLockedUntil());

        Instant loginTime = Instant.now();
        userRepo.updateLastLogin(account.getId(), loginTime);
        UserAccount afterLogin = userRepo.findById(account.getId()).orElseThrow();
        assertNotNull(afterLogin.getLastLoginAt());
    }
}
