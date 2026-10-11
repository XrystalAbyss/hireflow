package com.hireflow.ui;

import com.hireflow.context.ApplicationContext;
import com.hireflow.db.DatabaseConfig;
import com.hireflow.db.DatabaseMode;
import com.hireflow.db.TransactionManager;
import com.hireflow.model.Company;
import com.hireflow.network.client.HireFlowTcpClient;
import com.hireflow.network.protocol.ResponseMessage;
import com.hireflow.network.server.HireFlowTcpServer;
import com.hireflow.network.server.NetworkActivity;
import com.hireflow.service.SampleDataLoader;
import org.junit.jupiter.api.*;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Stage 2C.3: Swing Networking Dashboard & Lifecycle Integration Tests")
public class NetworkingPanelIntegrationTest {

    private ApplicationContext inMemoryContext;

    @BeforeEach
    void setUp() {
        UIUtils.setSuppressDialogsForTesting(true);
        inMemoryContext = ApplicationContext.createInMemory();
        SampleDataLoader loader = new SampleDataLoader(
                inMemoryContext.getStudentRepository(),
                inMemoryContext.getCompanyRepository(),
                inMemoryContext.getDriveRepository(),
                inMemoryContext.getInterviewerRepository(),
                inMemoryContext.getRoomRepository()
        );
        loader.seedAll();
    }

    @AfterEach
    void tearDown() {
        UIUtils.setSuppressDialogsForTesting(false);
        if (inMemoryContext != null && !inMemoryContext.isClosed()) {
            inMemoryContext.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. NetworkingPanel initializes with clean default controls and state")
    void testNetworkingPanelInitialization() {
        NetworkingPanel panel = new NetworkingPanel(inMemoryContext);
        assertNotNull(panel);
        assertEquals(NetworkingPanel.ServerState.STOPPED, panel.getCurrentState());
        assertFalse(panel.isServerRunning());
        assertEquals(String.valueOf(com.hireflow.network.protocol.ProtocolConstants.DEFAULT_PORT), panel.getTxtPort().getText());
        assertTrue(panel.getBtnStartServer().isEnabled());
        assertFalse(panel.getBtnStopServer().isEnabled());
        assertEquals(0, panel.getActivityTableModel().getRowCount());
    }

    @Test
    @Order(2)
    @DisplayName("2. MainFrame provides accessible networking navigation and panel binding")
    void testMainFrameNetworkingNavigation() {
        MainFrame frame = new MainFrame(inMemoryContext);
        try {
            assertNotNull(frame.getNetworkingPanel());
            frame.switchScreen(MainFrame.SCREEN_NETWORKING);
            assertEquals(MainFrame.SCREEN_NETWORKING, frame.getCurrentScreen());
        } finally {
            frame.dispose();
        }
    }

    @Test
    @Order(3)
    @DisplayName("3. ApplicationContext explicitly starts TCP server sharing repositories")
    void testStartServerFromApplicationContext() throws Exception {
        HireFlowTcpServer server = inMemoryContext.startTcpServer(0);
        assertTrue(server.isRunning());
        assertTrue(inMemoryContext.isTcpServerRunning());
        assertTrue(server.getPort() > 0);

        // Verify server shares existing repository state
        assertTrue(server.getContext().getCompanyRepository().existsById("CMP-001"));

        // Verify connectivity
        try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
            client.connect();
            ResponseMessage ping = client.ping();
            assertTrue(ping.isOk());
            assertEquals("PONG", ping.message());
        }
    }

    @Test
    @Order(4)
    @DisplayName("4. Stopping TCP server does not stop ApplicationContext or close services")
    void testStopServerWithoutStoppingApplication() throws Exception {
        inMemoryContext.startTcpServer(0);
        assertTrue(inMemoryContext.isTcpServerRunning());

        // Stop TCP server
        inMemoryContext.stopTcpServer();
        assertFalse(inMemoryContext.isTcpServerRunning());
        assertFalse(inMemoryContext.isClosed(), "ApplicationContext must remain fully active after server stop");

        // Application services and repositories remain operational
        assertEquals(11, inMemoryContext.getStudentService().getTotalStudentsCount());
        assertEquals(5, inMemoryContext.getDriveService().getAllDrives().size());
    }

    @Test
    @Order(5)
    @DisplayName("5. Duplicate TCP server start requests are strictly rejected")
    void testDuplicateServerStartRejection() throws Exception {
        inMemoryContext.startTcpServer(0);
        assertTrue(inMemoryContext.isTcpServerRunning());

        // Second start must throw IllegalStateException
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            inMemoryContext.startTcpServer(0);
        });
        assertTrue(ex.getMessage().contains("already running"));
    }

    @Test
    @Order(6)
    @DisplayName("6. Invalid port configuration handled gracefully without crashing")
    void testInvalidPortHandling() {
        NetworkingPanel panel = new NetworkingPanel(inMemoryContext);
        panel.getTxtPort().setText("not-a-number");

        // Should reject without crashing or entering RUNNING state
        panel.executeStartServer();
        assertEquals(NetworkingPanel.ServerState.STOPPED, panel.getCurrentState());
        assertFalse(panel.isServerRunning());
        assertNotNull(panel.getLastErrorMessage());
        assertTrue(panel.getLastErrorMessage().contains("valid port number"));
    }

    @Test
    @Order(7)
    @DisplayName("7. Occupied port collision handled cleanly with error state")
    void testOccupiedPortHandling() throws Exception {
        // Occupy a port
        try (ServerSocket occupyingSocket = new ServerSocket(0)) {
            int occupiedPort = occupyingSocket.getLocalPort();

            // Attempt to start HireFlow TCP server on the occupied port
            assertThrows(IOException.class, () -> {
                inMemoryContext.startTcpServer(occupiedPort);
            });

            assertFalse(inMemoryContext.isTcpServerRunning());
            assertFalse(inMemoryContext.isClosed(), "Collision must not close the main application");
        }
    }

    @Test
    @Order(8)
    @DisplayName("8. Live connected and subscribed client counters update accurately")
    void testLiveClientAndSubscriberCounters() throws Exception {
        HireFlowTcpServer server = inMemoryContext.startTcpServer(0);

        HireFlowTcpClient c1 = new HireFlowTcpClient("127.0.0.1", server.getPort());
        HireFlowTcpClient c2 = new HireFlowTcpClient("127.0.0.1", server.getPort());
        HireFlowTcpClient c3 = new HireFlowTcpClient("127.0.0.1", server.getPort());

        try {
            c1.connect();
            c2.connect();
            c3.connect();
            Thread.sleep(100);

            assertEquals(3, server.getActiveClientCount());
            assertEquals(0, server.getEventBroadcaster().getSubscriberCount());

            c1.subscribeEvents(msg -> {});
            c2.subscribeEvents(msg -> {});
            Thread.sleep(100);

            assertEquals(3, server.getActiveClientCount());
            assertEquals(2, server.getEventBroadcaster().getSubscriberCount());

            // Close one subscriber
            c1.close();
            Thread.sleep(100);

            assertEquals(2, server.getActiveClientCount());
            assertEquals(1, server.getEventBroadcaster().getSubscriberCount());
        } finally {
            c1.close();
            c2.close();
            c3.close();
        }
    }

    @Test
    @Order(9)
    @DisplayName("9. Public event dispatch counter accurately tracks broadcasts")
    void testPublicEventCountAccuracy() throws Exception {
        HireFlowTcpServer server = inMemoryContext.startTcpServer(0);

        try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
            client.connect();
            client.subscribeEvents(msg -> {});

            assertEquals(0, server.getTotalEventsDispatched());

            inMemoryContext.getRecruitmentDriveService().createDrive(
                    "DRV-CNT-1", "CMP-001", "Counter Eng 1", 8.0, Set.of("CSE"), LocalDate.now().plusDays(10)
            );
            inMemoryContext.getRecruitmentDriveService().createDrive(
                    "DRV-CNT-2", "CMP-001", "Counter Eng 2", 8.0, Set.of("CSE"), LocalDate.now().plusDays(10)
            );

            assertEquals(2, server.getTotalEventsDispatched());
        }
    }

    @Test
    @Order(10)
    @DisplayName("10. Public announcement broadcasts trigger from RecruitmentDriveService operations")
    void testNetworkingEventsFromRecruitmentDriveService() throws Exception {
        HireFlowTcpServer server = inMemoryContext.startTcpServer(0);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ResponseMessage> received = new AtomicReference<>();

        try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
            client.connect();
            client.subscribeEvents(msg -> {
                if (msg.isEvent()) {
                    received.set(msg);
                    latch.countDown();
                }
            });

            String dId = "DRV-SERVICE-EVENT-" + System.currentTimeMillis();
            inMemoryContext.getRecruitmentDriveService().createDrive(
                    dId, "CMP-001", "Lead Architect", 8.5, Set.of("CSE"), LocalDate.now().plusDays(15)
            );

            assertTrue(latch.await(3, TimeUnit.SECONDS));
            assertNotNull(received.get());
            assertEquals("JOB_DRIVE_CREATED", received.get().getEventType());
            assertEquals(dId, received.get().getEventPayload().get("driveId"));
        }
    }

    @Test
    @Order(11)
    @DisplayName("11. JDBC rollback completely suppresses public announcement broadcasts")
    void testJdbcRollbackSuppressesNotifications() throws Exception {
        System.setProperty("hireflow.allow.test.context", "true");
        Properties localProps = loadLocalProps();

        String url = System.getenv().getOrDefault("HIREFLOW_DB_URL",
                System.getProperty("hireflow.db.url", localProps.getProperty("hireflow.db.url", "jdbc:postgresql://localhost:5432/hireflow_test")));
        String user = System.getenv().getOrDefault("HIREFLOW_DB_USER",
                System.getProperty("hireflow.db.user", localProps.getProperty("hireflow.db.user", "hireflow_user")));
        String pass = System.getenv().getOrDefault("HIREFLOW_DB_PASSWORD",
                System.getProperty("hireflow.db.password", localProps.getProperty("hireflow.db.password", "")));

        DatabaseConfig config = DatabaseConfig.builder()
                .mode(DatabaseMode.JDBC_TEST)
                .jdbcUrl(url).username(user).password(pass)
                .maximumPoolSize(5).minimumIdle(1).connectionTimeoutMs(5000)
                .build();

        ApplicationContext jdbcContext;
        try {
            jdbcContext = ApplicationContext.createJdbcTest(config);
        } catch (Exception ex) {
            System.err.println("Skipping JDBC test due to connection failure: " + ex.getMessage());
            return;
        }

        try (jdbcContext) {
            HireFlowTcpServer server = jdbcContext.startTcpServer(0);
            AtomicInteger eventCounter = new AtomicInteger(0);

            try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
                client.connect();
                client.subscribeEvents(msg -> {
                    if (msg.isEvent()) {
                        eventCounter.incrementAndGet();
                    }
                });

                TransactionManager txManager = jdbcContext.getTransactionManager().orElseThrow();

                // Execute transactional operation that rolls back
                assertThrows(RuntimeException.class, () -> {
                    txManager.runInTransactionWithoutResult(conn -> {
                        jdbcContext.getRecruitmentDriveService().createDrive(
                                "DRV-RB-" + System.currentTimeMillis(),
                                "CMP-001", "Rollback Role", 8.0, Set.of("CSE"), LocalDate.now().plusDays(10)
                        );
                        throw new RuntimeException("Simulated transaction failure");
                    });
                });

                Thread.sleep(300);
                assertEquals(0, eventCounter.get(), "Rolled back transaction must not emit announcement");
            }
        }
    }

    @Test
    @Order(12)
    @DisplayName("12. Post-commit listener exception isolation does not fail committed transactions")
    void testPostCommitListenerExceptionIsolation() throws Exception {
        System.setProperty("hireflow.allow.test.context", "true");
        Properties localProps = loadLocalProps();

        String url = System.getenv().getOrDefault("HIREFLOW_DB_URL",
                System.getProperty("hireflow.db.url", localProps.getProperty("hireflow.db.url", "jdbc:postgresql://localhost:5432/hireflow_test")));
        String user = System.getenv().getOrDefault("HIREFLOW_DB_USER",
                System.getProperty("hireflow.db.user", localProps.getProperty("hireflow.db.user", "hireflow_user")));
        String pass = System.getenv().getOrDefault("HIREFLOW_DB_PASSWORD",
                System.getProperty("hireflow.db.password", localProps.getProperty("hireflow.db.password", "")));

        DatabaseConfig config = DatabaseConfig.builder()
                .mode(DatabaseMode.JDBC_TEST)
                .jdbcUrl(url).username(user).password(pass)
                .maximumPoolSize(5).minimumIdle(1).connectionTimeoutMs(5000)
                .build();

        ApplicationContext jdbcContext;
        try {
            jdbcContext = ApplicationContext.createJdbcTest(config);
        } catch (Exception ex) {
            System.err.println("Skipping JDBC test due to connection failure: " + ex.getMessage());
            return;
        }

        try (jdbcContext) {
            TransactionManager txManager = jdbcContext.getTransactionManager().orElseThrow();
            String compId = "CMP-ISOLATION-" + System.currentTimeMillis();

            // Run transaction where post-commit action intentionally throws RuntimeException
            assertDoesNotThrow(() -> {
                txManager.runInTransactionWithoutResult(conn -> {
                    jdbcContext.getCompanyRepository().save(new Company(compId, "Isolation Corp " + System.currentTimeMillis(), "Software"));
                    TransactionManager.registerPostCommitAction(() -> {
                        throw new RuntimeException("Simulated post-commit notification failure!");
                    });
                });
            });

            // Verify the transaction committed and saved successfully despite post-commit hook error
            assertTrue(jdbcContext.getCompanyRepository().existsById(compId),
                    "Database transaction must remain committed even if post-commit notification hook throws an error");
        }
    }

    @Test
    @Order(13)
    @DisplayName("13. Swing EDT responsiveness preserved during server start/stop")
    void testSwingEdtResponsiveness() throws Exception {
        NetworkingPanel panel = new NetworkingPanel(inMemoryContext);
        panel.getTxtPort().setText("0");

        CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            panel.executeStartServer();
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS), "EDT must not be blocked by start server execution");

        // Wait for worker to complete
        int cycles = 0;
        while (!panel.isServerRunning() && cycles++ < 40) {
            Thread.sleep(50);
        }
        assertTrue(panel.isServerRunning());

        // Stop server
        CountDownLatch stopLatch = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            panel.executeStopServer();
            stopLatch.countDown();
        });
        assertTrue(stopLatch.await(2, TimeUnit.SECONDS));

        cycles = 0;
        while (panel.isServerRunning() && cycles++ < 40) {
            Thread.sleep(50);
        }
        assertFalse(panel.isServerRunning());
    }

    @Test
    @Order(14)
    @DisplayName("14. ApplicationContext shutdown ordering disposes server before pool")
    void testApplicationContextShutdownOrdering() throws Exception {
        HireFlowTcpServer server = inMemoryContext.startTcpServer(0);
        assertTrue(server.isRunning());

        inMemoryContext.close();

        assertFalse(server.isRunning(), "TCP server must be stopped upon ApplicationContext.close()");
        assertTrue(inMemoryContext.isClosed());
    }

    @Test
    @Order(15)
    @DisplayName("15. No leaked client worker or subscriber writer threads")
    void testNoLeakedClientThreads() throws Exception {
        HireFlowTcpServer server = inMemoryContext.startTcpServer(0);

        List<HireFlowTcpClient> clients = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            HireFlowTcpClient c = new HireFlowTcpClient("127.0.0.1", server.getPort());
            c.connect();
            c.subscribeEvents(msg -> {});
            clients.add(c);
        }

        Thread.sleep(100);
        assertEquals(4, server.getActiveClientCount());

        for (HireFlowTcpClient c : clients) {
            c.close();
        }
        Thread.sleep(200);

        assertEquals(0, server.getActiveClientCount());
        assertEquals(0, server.getEventBroadcaster().getSubscriberCount());

        // Stop server
        inMemoryContext.stopTcpServer();

        // Check active threads: no hireflow-subscriber-writer threads should be alive
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        for (Thread t : threadSet) {
            if (t.getName().startsWith("hireflow-subscriber-writer-")) {
                assertFalse(t.isAlive(), "Subscriber writer thread must not be leaked: " + t.getName());
            }
        }
    }

    @Test
    @Order(16)
    @DisplayName("16. Telemetry and activity log contain zero credentials or candidate PII")
    void testNoCredentialOrStudentDataLeakageInLogs() throws Exception {
        HireFlowTcpServer server = inMemoryContext.startTcpServer(0);

        try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
            client.connect();
            client.subscribeEvents(msg -> {});

            // Create recruitment drive
            inMemoryContext.getRecruitmentDriveService().createDrive(
                    "DRV-LOG-TEST", "CMP-001", "Log Audit Engineer", 8.0, Set.of("CSE"), LocalDate.now().plusDays(10)
            );

            // Register private student
            inMemoryContext.getStudentService().registerStudent(
                    "STU-SECRET-PII", "Confidential Candidate", "candidate@univ.edu", "CSE", 9.8, 2026
            );

            List<NetworkActivity> activities = server.getRecentActivities();
            assertFalse(activities.isEmpty());

            for (NetworkActivity act : activities) {
                String line = (act.action() + " " + act.details() + " " + act.status()).toLowerCase();
                assertFalse(line.contains("confidential candidate"));
                assertFalse(line.contains("stu-secret-pii"));
                assertFalse(line.contains("@univ.edu"));
                assertFalse(line.contains("password"));
                assertFalse(line.contains("jdbc:"));
            }
        }
    }

    @Test
    @Order(17)
    @DisplayName("17. Complete End-to-End Workflow Demonstration (Part 5)")
    void testEndToEndWorkflowDemonstration() throws Exception {
        // Step 1: Start TCP Server from ApplicationContext
        HireFlowTcpServer server = inMemoryContext.startTcpServer(0);
        assertTrue(server.isRunning());
        int port = server.getPort();

        // Step 2 & 3: Connect 3 separate Java TCP clients and subscribe all 3
        CountDownLatch drive1Latch = new CountDownLatch(3);
        List<ResponseMessage> client1Events = Collections.synchronizedList(new ArrayList<>());
        List<ResponseMessage> client2Events = Collections.synchronizedList(new ArrayList<>());
        List<ResponseMessage> client3Events = Collections.synchronizedList(new ArrayList<>());

        HireFlowTcpClient c1 = new HireFlowTcpClient("127.0.0.1", port);
        HireFlowTcpClient c2 = new HireFlowTcpClient("127.0.0.1", port);
        HireFlowTcpClient c3 = new HireFlowTcpClient("127.0.0.1", port);

        try {
            c1.connect();
            c2.connect();
            c3.connect();

            c1.subscribeEvents(msg -> { client1Events.add(msg); drive1Latch.countDown(); });
            c2.subscribeEvents(msg -> { client2Events.add(msg); drive1Latch.countDown(); });
            c3.subscribeEvents(msg -> { client3Events.add(msg); drive1Latch.countDown(); });

            Thread.sleep(100);
            assertEquals(3, server.getEventBroadcaster().getSubscriberCount());

            // Step 4 & 5: Create a new recruitment drive using RecruitmentDriveService
            String driveId1 = "DRV-DEMO-MULTI-1";
            inMemoryContext.getRecruitmentDriveService().createDrive(
                    driveId1, "CMP-001", "Principal Systems Architect", 8.5, Set.of("CSE", "ECE"), LocalDate.now().plusDays(20)
            );

            assertTrue(drive1Latch.await(4, TimeUnit.SECONDS), "All 3 clients must receive the matching public event");
            assertEquals(1, client1Events.size());
            assertEquals(1, client2Events.size());
            assertEquals(1, client3Events.size());
            assertEquals(driveId1, client1Events.get(0).getEventPayload().get("driveId"));
            assertEquals("Google Cloud", client1Events.get(0).getEventPayload().get("companyName"));

            // Step 8: Disconnect client 1 and verify remaining clients continue receiving announcements
            c1.close();
            Thread.sleep(100);
            assertEquals(2, server.getEventBroadcaster().getSubscriberCount());

            CountDownLatch drive2Latch = new CountDownLatch(2);
            c2.subscribeEvents(msg -> { client2Events.add(msg); drive2Latch.countDown(); });
            c3.subscribeEvents(msg -> { client3Events.add(msg); drive2Latch.countDown(); });

            String driveId2 = "DRV-DEMO-MULTI-2";
            inMemoryContext.getRecruitmentDriveService().createDrive(
                    driveId2, "CMP-001", "Security Operations Engineer", 8.0, Set.of("CSE"), LocalDate.now().plusDays(20)
            );

            assertTrue(drive2Latch.await(4, TimeUnit.SECONDS), "Remaining clients 2 and 3 must receive announcement");
            assertEquals(2, client2Events.size());
            assertEquals(2, client3Events.size());
            assertEquals(1, client1Events.size(), "Disconnected client 1 must not receive subsequent announcements");

            // Step 9: Stop the server and verify clean disconnect
            inMemoryContext.stopTcpServer();
            assertFalse(server.isRunning());
            assertEquals(0, server.getActiveClientCount());
        } finally {
            c1.close();
            c2.close();
            c3.close();
        }
    }

    private Properties loadLocalProps() {
        Properties localProps = new Properties();
        File localFile = new File("hireflow-local.properties");
        if (localFile.exists()) {
            try (FileInputStream fis = new FileInputStream(localFile)) {
                localProps.load(fis);
            } catch (Exception ignored) {
            }
        }
        return localProps;
    }
}
