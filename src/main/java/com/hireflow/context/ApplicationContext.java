package com.hireflow.context;

import com.hireflow.db.*;
import com.hireflow.exception.DatabaseException;
import com.hireflow.repository.*;
import com.hireflow.scheduler.ConflictChecker;
import com.hireflow.scheduler.GreedyScheduler;
import com.hireflow.scheduler.SchedulingEngine;
import com.hireflow.service.*;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Centralized application context and repository factory for HireFlow.
 * <p>
 * Manages the unified lifecycle of repositories, services, database connectivity,
 * connection pooling, Flyway migrations, and transactions according to {@link DatabaseMode}:
 * <ul>
 *     <li><b>IN_MEMORY</b>: Pure Stage 1 in-memory repositories and services. Requires no DB.</li>
 *     <li><b>JDBC_DEV</b>: HikariCP connection pool connected to {@code hireflow_dev}, automated
 *         Flyway migrations, transactional JDBC repositories, and persistent services.</li>
 *     <li><b>JDBC_TEST</b>: Dedicated mode for automated integration testing against {@code hireflow_test}.
 *         Protected by strict safeguards to prevent accidental desktop launch.</li>
 * </ul>
 * Guarantees 100% consistent repository modes within each context (no partial in-memory / JDBC mixing)
 * and safe, idempotent pool closure on shutdown.
 */
public class ApplicationContext implements AutoCloseable {

    private final DatabaseConfig config;
    private final DatabaseMode mode;

    // Infrastructure services (null in IN_MEMORY mode)
    private final DataSourceProvider dataSourceProvider;
    private final TransactionManager transactionManager;
    private final FlywayMigrationService migrationService;

    // Repositories
    private final StudentRepository studentRepository;
    private final CompanyRepository companyRepository;
    private final DriveRepository driveRepository;
    private final ApplicationRepository applicationRepository;
    private final InterviewerRepository interviewerRepository;
    private final RoomRepository roomRepository;
    private final InterviewSessionRepository sessionRepository;

    // Domain Services
    private final StudentService studentService;
    private final RecruitmentDriveService driveService;
    private final EligibilityService eligibilityService;
    private final ApplicationService applicationService;
    private final InterviewService interviewService;
    private final SchedulingEngine schedulingEngine;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Thread shutdownHook;

    private ApplicationContext(DatabaseConfig config, boolean allowTestMode) {
        this.config = Objects.requireNonNull(config, "DatabaseConfig cannot be null");
        this.mode = config.getMode();

        // Safeguard: JDBC_TEST mode cannot be selected accidentally in normal launch
        if (this.mode == DatabaseMode.JDBC_TEST && !allowTestMode) {
            String testGuiAllowed = System.getProperty("hireflow.allow.test.context", "false");
            if (!"true".equalsIgnoreCase(testGuiAllowed)) {
                throw new IllegalStateException(
                        "JDBC_TEST mode is reserved for automated integration testing and cannot be selected " +
                                "for normal application launch. Use IN_MEMORY or JDBC_DEV instead."
                );
            }
        }

        DataSourceProvider dsProvider = null;
        TransactionManager txMgr = null;
        FlywayMigrationService flywaySvc = null;
        StudentRepository sRepo;
        CompanyRepository cRepo;
        DriveRepository dRepo;
        ApplicationRepository aRepo;
        InterviewerRepository iRepo;
        RoomRepository rRepo;
        InterviewSessionRepository sessRepo;

        if (this.mode == DatabaseMode.IN_MEMORY) {
            // 1. In-Memory Mode: pure in-memory stores, no HikariCP or database connection
            sRepo = new InMemoryStudentRepository();
            cRepo = new InMemoryCompanyRepository();
            dRepo = new InMemoryDriveRepository();
            aRepo = new InMemoryApplicationRepository();
            iRepo = new InMemoryInterviewerRepository();
            rRepo = new InMemoryRoomRepository();
            sessRepo = new InMemoryInterviewSessionRepository();
        } else {
            // 2. JDBC Modes (JDBC_DEV or JDBC_TEST)
            try {
                dsProvider = new DataSourceProvider(config);

                // Verify connectivity with a 5-second timeout
                if (!dsProvider.verifyConnectionHealth(5)) {
                    throw new DatabaseException(
                            "Could not establish connection to PostgreSQL at '" + config.getJdbcUrl() + "'."
                    );
                }

                // Run Flyway migrations
                flywaySvc = new FlywayMigrationService(dsProvider.getDataSource(), config.isBaselineOnMigrate());
                flywaySvc.migrate();

                // Initialize TransactionManager
                txMgr = new TransactionManager(dsProvider.getDataSource());

                // Instantiate all seven JDBC repositories sharing the TransactionManager
                sRepo = new JdbcStudentRepository(txMgr);
                cRepo = new JdbcCompanyRepository(txMgr);
                dRepo = new JdbcDriveRepository(txMgr);
                aRepo = new JdbcApplicationRepository(txMgr);
                iRepo = new JdbcInterviewerRepository(txMgr);
                rRepo = new JdbcRoomRepository(txMgr);
                sessRepo = new JdbcInterviewSessionRepository(txMgr);
            } catch (Exception ex) {
                // If anything fails during JDBC initialization, ensure cleanup
                if (dsProvider != null && !dsProvider.isClosed()) {
                    try {
                        dsProvider.close();
                    } catch (Exception ignored) {
                    }
                }
                String sanitized = SQLExceptionTranslator.sanitizeMessage(ex.getMessage());
                if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                    sanitized = sanitized.replace(config.getPassword(), "******");
                }
                throw new DatabaseException("Failed to initialize JDBC application context: " + sanitized, ex);
            }
        }

        this.dataSourceProvider = dsProvider;
        this.transactionManager = txMgr;
        this.migrationService = flywaySvc;
        this.studentRepository = sRepo;
        this.companyRepository = cRepo;
        this.driveRepository = dRepo;
        this.applicationRepository = aRepo;
        this.interviewerRepository = iRepo;
        this.roomRepository = rRepo;
        this.sessionRepository = sessRepo;

        // 3. Construct and wire domain services uniformly
        this.eligibilityService = new EligibilityService();
        this.studentService = new StudentService(this.studentRepository, this.applicationRepository);
        this.driveService = new RecruitmentDriveService(this.driveRepository, this.companyRepository, this.applicationRepository);
        this.applicationService = new ApplicationService(
                this.applicationRepository,
                this.studentRepository,
                this.driveRepository,
                this.eligibilityService,
                this.sessionRepository
        );
        this.interviewService = new InterviewService(
                this.sessionRepository,
                this.applicationRepository,
                this.applicationService,
                new ConflictChecker(),
                this.transactionManager
        );
        this.schedulingEngine = new GreedyScheduler();

        // 4. Register JVM shutdown hook for clean resource disposal
        this.shutdownHook = new Thread(this::close, "HireFlow-Context-ShutdownHook");
        try {
            Runtime.getRuntime().addShutdownHook(this.shutdownHook);
        } catch (IllegalStateException ignored) {
            // Already shutting down
        }
    }

    /**
     * Creates an ApplicationContext using standard configuration resolution rules.
     */
    public static ApplicationContext create() {
        return create(DatabaseConfig.load(), false);
    }

    /**
     * Creates an ApplicationContext with custom DatabaseConfig.
     */
    public static ApplicationContext create(DatabaseConfig config) {
        return create(config, false);
    }

    /**
     * Creates an ApplicationContext with custom DatabaseConfig and explicit test-mode allowance.
     */
    public static ApplicationContext create(DatabaseConfig config, boolean allowTestMode) {
        return new ApplicationContext(config, allowTestMode);
    }

    /**
     * Creates a guaranteed in-memory ApplicationContext (requires no DB connection).
     */
    public static ApplicationContext createInMemory() {
        DatabaseConfig inMemCfg = DatabaseConfig.builder()
                .mode(DatabaseMode.IN_MEMORY)
                .build();
        return new ApplicationContext(inMemCfg, false);
    }

    /**
     * Creates a development PostgreSQL ApplicationContext connecting to {@code hireflow_dev}.
     */
    public static ApplicationContext createJdbcDev() {
        DatabaseConfig loaded = DatabaseConfig.load();
        DatabaseConfig devConfig = DatabaseConfig.builder()
                .mode(DatabaseMode.JDBC_DEV)
                .jdbcUrl(loaded.getJdbcUrl())
                .username(loaded.getUsername())
                .password(loaded.getPassword())
                .maximumPoolSize(loaded.getMaximumPoolSize())
                .minimumIdle(loaded.getMinimumIdle())
                .connectionTimeoutMs(loaded.getConnectionTimeoutMs())
                .idleTimeoutMs(loaded.getIdleTimeoutMs())
                .maxLifetimeMs(loaded.getMaxLifetimeMs())
                .leakDetectionThresholdMs(loaded.getLeakDetectionThresholdMs())
                .build();
        return new ApplicationContext(devConfig, false);
    }

    /**
     * Creates an integration testing ApplicationContext connecting to {@code hireflow_test}.
     */
    public static ApplicationContext createJdbcTest(DatabaseConfig testConfig) {
        return new ApplicationContext(testConfig, true);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                if (shutdownHook != null) {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                }
            } catch (Exception ignored) {
            }

            if (dataSourceProvider != null && !dataSourceProvider.isClosed()) {
                dataSourceProvider.close();
            }
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

    public DatabaseMode getMode() {
        return mode;
    }

    public DatabaseConfig getConfig() {
        return config;
    }

    public Optional<DataSourceProvider> getDataSourceProvider() {
        return Optional.ofNullable(dataSourceProvider);
    }

    public Optional<TransactionManager> getTransactionManager() {
        return Optional.ofNullable(transactionManager);
    }

    public Optional<FlywayMigrationService> getFlywayMigrationService() {
        return Optional.ofNullable(migrationService);
    }

    public StudentRepository getStudentRepository() {
        return studentRepository;
    }

    public CompanyRepository getCompanyRepository() {
        return companyRepository;
    }

    public DriveRepository getDriveRepository() {
        return driveRepository;
    }

    public ApplicationRepository getApplicationRepository() {
        return applicationRepository;
    }

    public InterviewerRepository getInterviewerRepository() {
        return interviewerRepository;
    }

    public RoomRepository getRoomRepository() {
        return roomRepository;
    }

    public InterviewSessionRepository getSessionRepository() {
        return sessionRepository;
    }

    public StudentService getStudentService() {
        return studentService;
    }

    public RecruitmentDriveService getDriveService() {
        return driveService;
    }

    public EligibilityService getEligibilityService() {
        return eligibilityService;
    }

    public ApplicationService getApplicationService() {
        return applicationService;
    }

    public InterviewService getInterviewService() {
        return interviewService;
    }

    public SchedulingEngine getSchedulingEngine() {
        return schedulingEngine;
    }

    /**
     * Returns sanitized diagnostic information suitable for UI status bars and logging.
     * Never exposes database credentials.
     */
    public String getSafeDiagnosticInfo() {
        if (mode.isInMemory()) {
            return "Mode: IN_MEMORY | Repositories: 7 In-Memory | Active";
        }
        String sanitizedUrl = SQLExceptionTranslator.sanitizeMessage(config.getJdbcUrl());
        return String.format("Mode: %s | Database: %s | Pool: %d max, %d min",
                mode.name(), sanitizedUrl, config.getMaximumPoolSize(), config.getMinimumIdle());
    }
}
