package com.hireflow.acceptance;

import com.hireflow.context.ApplicationContext;
import com.hireflow.db.DatabaseConfig;
import com.hireflow.db.DatabaseMode;
import com.hireflow.db.FlywayMigrationService;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.network.client.HireFlowTcpClient;
import com.hireflow.network.protocol.ResponseMessage;
import com.hireflow.network.server.HireFlowTcpServer;
import com.hireflow.service.SampleDataLoader;
import com.hireflow.ui.CompanyDrivePanel;
import com.hireflow.ui.MainFrame;
import com.hireflow.ui.NetworkingPanel;
import com.hireflow.ui.UIUtils;
import org.junit.jupiter.api.*;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Stage 2C.4: Final CAP-5 Acceptance & Verification Suite")
public class Stage2FinalAcceptanceTest {

    private static DatabaseConfig testConfig;

    @BeforeAll
    static void setUpAll() {
        UIUtils.setSuppressDialogsForTesting(true);

        Properties localProps = new Properties();
        File localFile = new File("hireflow-local.properties");
        if (localFile.exists()) {
            try (FileInputStream fis = new FileInputStream(localFile)) {
                localProps.load(fis);
            } catch (Exception ignored) {
            }
        }

        String url = System.getenv().getOrDefault("HIREFLOW_DB_URL",
                System.getProperty("hireflow.db.url", localProps.getProperty("hireflow.db.url", com.hireflow.testsupport.CoreTestDatabase.url())));
        String user = System.getenv().getOrDefault("HIREFLOW_DB_USER",
                System.getProperty("hireflow.db.user", localProps.getProperty("hireflow.db.user", "hireflow_user")));
        String pass = System.getenv().getOrDefault("HIREFLOW_DB_PASSWORD",
                System.getProperty("hireflow.db.password", localProps.getProperty("hireflow.db.password", "")));

        testConfig = DatabaseConfig.builder()
                .mode(DatabaseMode.JDBC_TEST)
                .jdbcUrl(url).username(user).password(pass)
                .maximumPoolSize(5).minimumIdle(1).connectionTimeoutMs(5000)
                .build();
    }

    @AfterAll
    static void tearDownAll() {
        UIUtils.setSuppressDialogsForTesting(false);
        System.clearProperty("hireflow.allow.test.context");
    }

    @Test
    @Order(1)
    @DisplayName("Part 2 Acceptance: Full Swing GUI Workflow, Multi-Client Socket Broadcast, and PostgreSQL Verification")
    void testCompleteSwingGuiNetworkingAndPersistenceWorkflow() throws Exception {
        // Step 1: Launch HireFlow in JDBC Mode
        ApplicationContext context1 = ApplicationContext.createJdbcTest(testConfig);
        SampleDataLoader loader = new SampleDataLoader(
                context1.getStudentRepository(),
                context1.getCompanyRepository(),
                context1.getDriveRepository(),
                context1.getInterviewerRepository(),
                context1.getRoomRepository()
        );
        loader.seedAll();

        MainFrame mainFrame = new MainFrame(context1);
        String uniqueSuffix = String.valueOf(System.currentTimeMillis() % 100000);
        String driveId1 = "DRV-GUI-ACC-A" + uniqueSuffix;
        String driveId2 = "DRV-GUI-ACC-B" + uniqueSuffix;

        try {
            // Step 2: Open the Networking screen
            mainFrame.switchScreen(MainFrame.SCREEN_NETWORKING);
            assertEquals(MainFrame.SCREEN_NETWORKING, mainFrame.getCurrentScreen());

            NetworkingPanel netPanel = mainFrame.getNetworkingPanel();
            assertNotNull(netPanel);
            assertEquals(NetworkingPanel.ServerState.STOPPED, netPanel.getCurrentState());

            // Step 3: Start the TCP server from the Swing Start button (port 0 for ephemeral dynamic port)
            netPanel.getTxtPort().setText("0");
            SwingUtilities.invokeLater(() -> netPanel.getBtnStartServer().doClick());

            // Wait for SwingWorker to bind server
            int waitCycles = 0;
            while (!netPanel.isServerRunning() && waitCycles++ < 50) {
                Thread.sleep(50);
            }
            assertTrue(netPanel.isServerRunning(), "TCP Server must be RUNNING via Swing Start button click");
            HireFlowTcpServer server = netPanel.getActiveServer();
            assertNotNull(server);
            int serverPort = server.getPort();
            assertTrue(serverPort > 0);

            // Step 4 & 5: Connect three independent Java TCP clients and subscribe all three
            List<ResponseMessage> client1Events = new CopyOnWriteArrayList<>();
            List<ResponseMessage> client2Events = new CopyOnWriteArrayList<>();
            List<ResponseMessage> client3Events = new CopyOnWriteArrayList<>();
            CountDownLatch drive1Latch = new CountDownLatch(3);

            HireFlowTcpClient client1 = new HireFlowTcpClient("127.0.0.1", serverPort);
            HireFlowTcpClient client2 = new HireFlowTcpClient("127.0.0.1", serverPort);
            HireFlowTcpClient client3 = new HireFlowTcpClient("127.0.0.1", serverPort);

            try {
                client1.connect();
                client2.connect();
                client3.connect();

                client1.subscribeEvents(msg -> { if (msg.isEvent()) { client1Events.add(msg); drive1Latch.countDown(); } });
                client2.subscribeEvents(msg -> { if (msg.isEvent()) { client2Events.add(msg); drive1Latch.countDown(); } });
                client3.subscribeEvents(msg -> { if (msg.isEvent()) { client3Events.add(msg); drive1Latch.countDown(); } });

                Thread.sleep(150);
                assertEquals(3, server.getActiveClientCount());
                assertEquals(3, server.getEventBroadcaster().getSubscriberCount());

                // Step 6: Navigate to Companies & Recruitment Drives in Swing
                mainFrame.switchScreen(MainFrame.SCREEN_DRIVES);
                assertEquals(MainFrame.SCREEN_DRIVES, mainFrame.getCurrentScreen());

                CompanyDrivePanel drivePanel = mainFrame.getCompanyDrivePanel();
                assertNotNull(drivePanel);

                // Step 7: Create a genuinely new recruitment drive using actual GUI form fields and submit button click
                SwingUtilities.invokeAndWait(() -> {
                    drivePanel.submitDriveForm(
                            driveId1,
                            "CMP-001 - Google Cloud",
                            "Full-Stack Systems Architect",
                            8.25,
                            "CSE, ECE",
                            LocalDate.now().plusDays(25).toString()
                    );
                });

                // Step 8: Confirm PostgreSQL persisted the new drive and its eligible branches
                Optional<RecruitmentDrive> persistedDrive1 = context1.getDriveRepository().findById(driveId1);
                assertTrue(persistedDrive1.isPresent(), "PostgreSQL must persist new drive: " + driveId1);
                assertEquals("Full-Stack Systems Architect", persistedDrive1.get().getJobTitle());
                assertEquals(8.25, persistedDrive1.get().getMinimumCgpa());
                assertTrue(persistedDrive1.get().getEligibleBranches().contains("CSE"));
                assertTrue(persistedDrive1.get().getEligibleBranches().contains("ECE"));

                // Step 9: Verify all three subscribers receive exactly one JOB_DRIVE_CREATED event
                assertTrue(drive1Latch.await(5, TimeUnit.SECONDS), "All 3 clients must receive announcement");
                assertEquals(1, client1Events.size());
                assertEquals(1, client2Events.size());
                assertEquals(1, client3Events.size());
                assertEquals("JOB_DRIVE_CREATED", client1Events.get(0).getEventType());
                assertEquals(driveId1, client1Events.get(0).getEventPayload().get("driveId"));

                // Step 10: Verify the NetworkingPanel updates subscriber and event counters
                mainFrame.switchScreen(MainFrame.SCREEN_NETWORKING);
                Thread.sleep(150);
                assertEquals(1, server.getTotalEventsDispatched());
                assertEquals(3, server.getEventBroadcaster().getSubscriberCount());

                // Step 11: Disconnect Client A
                client1.close();
                Thread.sleep(150);
                assertEquals(2, server.getActiveClientCount());
                assertEquals(2, server.getEventBroadcaster().getSubscriberCount());

                // Step 12: Create another drive through Swing GUI
                mainFrame.switchScreen(MainFrame.SCREEN_DRIVES);
                CountDownLatch drive2Latch = new CountDownLatch(2);
                client2.subscribeEvents(msg -> { if (msg.isEvent() && driveId2.equals(msg.getEventPayload().get("driveId"))) drive2Latch.countDown(); });
                client3.subscribeEvents(msg -> { if (msg.isEvent() && driveId2.equals(msg.getEventPayload().get("driveId"))) drive2Latch.countDown(); });

                SwingUtilities.invokeAndWait(() -> {
                    drivePanel.submitDriveForm(
                            driveId2,
                            "CMP-001 - Google Cloud",
                            "Security Reliability Engineer",
                            8.0,
                            "CSE, IT",
                            LocalDate.now().plusDays(30).toString()
                    );
                });

                // Step 13: Confirm Clients B and C receive the next announcement; disconnected Client A receives 0 additional
                assertTrue(drive2Latch.await(5, TimeUnit.SECONDS), "Clients B & C must receive second announcement");
                assertEquals(1, client1Events.size(), "Disconnected Client 1 must not receive subsequent events");
                assertTrue(context1.getDriveRepository().existsById(driveId2));

                // Step 14: Stop the server through Swing and confirm clean shutdown
                mainFrame.switchScreen(MainFrame.SCREEN_NETWORKING);
                SwingUtilities.invokeLater(() -> netPanel.getBtnStopServer().doClick());

                waitCycles = 0;
                while (netPanel.getCurrentState() != NetworkingPanel.ServerState.STOPPED && waitCycles++ < 50) {
                    Thread.sleep(50);
                }
                assertFalse(netPanel.isServerRunning(), "TCP Server must be STOPPED via Swing Stop button");
                assertEquals(NetworkingPanel.ServerState.STOPPED, netPanel.getCurrentState());
            } finally {
                client1.close();
                client2.close();
                client3.close();
            }
        } finally {
            mainFrame.dispose();
            context1.close();
        }

        // Step 15: Restart HireFlow and verify both new drives remain in PostgreSQL across restarts
        try (ApplicationContext context2 = ApplicationContext.createJdbcTest(testConfig)) {
            Optional<RecruitmentDrive> d1 = context2.getDriveRepository().findById(driveId1);
            Optional<RecruitmentDrive> d2 = context2.getDriveRepository().findById(driveId2);

            assertTrue(d1.isPresent(), "Drive 1 must persist across application restarts in PostgreSQL");
            assertEquals("Full-Stack Systems Architect", d1.get().getJobTitle());
            assertTrue(d1.get().getEligibleBranches().contains("CSE"));

            assertTrue(d2.isPresent(), "Drive 2 must persist across application restarts in PostgreSQL");
            assertEquals("Security Reliability Engineer", d2.get().getJobTitle());
            assertTrue(d2.get().getEligibleBranches().contains("IT"));
        }
    }

    @Test
    @Order(2)
    @DisplayName("Part 4 Acceptance: In-Memory Mode operates completely independently of PostgreSQL")
    void testInMemoryModeOperatesWithoutDatabase() {
        try (ApplicationContext inMem = ApplicationContext.createInMemory()) {
            SampleDataLoader loader = new SampleDataLoader(
                    inMem.getStudentRepository(),
                    inMem.getCompanyRepository(),
                    inMem.getDriveRepository(),
                    inMem.getInterviewerRepository(),
                    inMem.getRoomRepository()
            );
            loader.seedAll();

            assertEquals(11, inMem.getStudentService().getTotalStudentsCount());
            assertEquals(5, inMem.getDriveService().getAllDrives().size());
            assertFalse(inMem.getTransactionManager().isPresent(), "In-memory mode must have no active TransactionManager");
        }
    }
}
