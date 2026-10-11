package com.hireflow.network;

import com.hireflow.context.ApplicationContext;
import com.hireflow.db.DatabaseConfig;
import com.hireflow.db.DatabaseMode;
import com.hireflow.db.TransactionManager;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Student;
import com.hireflow.network.client.HireFlowTcpClient;
import com.hireflow.network.protocol.ProtocolConstants;
import com.hireflow.network.protocol.ResponseMessage;
import com.hireflow.network.server.HireFlowTcpServer;
import com.hireflow.service.SampleDataLoader;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Stage 2C.2: Reliable Multi-Client TCP Event Broadcasting & Subscription Tests")
public class HireFlowEventBroadcastingTest {

    private ApplicationContext inMemoryContext;
    private HireFlowTcpServer server;

    @BeforeEach
    void setUp() {
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
        if (server != null && server.isRunning()) {
            server.stop();
        }
        if (inMemoryContext != null && !inMemoryContext.isClosed()) {
            inMemoryContext.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. Three simultaneous subscribers receiving one public event")
    void testThreeSimultaneousSubscribers() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        CountDownLatch latch = new CountDownLatch(3);
        List<HireFlowTcpClient> clients = new ArrayList<>();
        List<ResponseMessage> received = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int i = 0; i < 3; i++) {
                HireFlowTcpClient c = new HireFlowTcpClient("127.0.0.1", server.getPort());
                c.connect();
                c.subscribeEvents(msg -> {
                    received.add(msg);
                    latch.countDown();
                });
                clients.add(c);
            }

            // Create drive
            String driveId = "DRV-BROADCAST-3SUB-" + System.currentTimeMillis();
            inMemoryContext.getRecruitmentDriveService().createDrive(
                    driveId, "CMP-001", "Cloud Platform Engineer", 8.0, Set.of("CSE"), LocalDate.now().plusDays(10)
            );

            assertTrue(latch.await(5, TimeUnit.SECONDS), "All 3 subscribers must receive the public event");
            assertEquals(3, received.size());
            for (ResponseMessage r : received) {
                assertTrue(r.isEvent());
                assertEquals("JOB_DRIVE_CREATED", r.getEventType());
                assertEquals(driveId, r.getEventPayload().get("driveId"));
            }
        } finally {
            for (HireFlowTcpClient c : clients) {
                c.close();
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("2. Ten connected subscribers receiving multiple sequential events")
    void testTenSubscribersMultipleEvents() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        int clientCount = 10;
        int eventCount = 3;
        CountDownLatch totalLatch = new CountDownLatch(clientCount * eventCount);
        List<HireFlowTcpClient> clients = new ArrayList<>();
        Map<Integer, AtomicInteger> counts = new ConcurrentHashMap<>();

        try {
            for (int i = 0; i < clientCount; i++) {
                final int clientId = i;
                counts.put(clientId, new AtomicInteger(0));
                HireFlowTcpClient c = new HireFlowTcpClient("127.0.0.1", server.getPort());
                c.connect();
                c.subscribeEvents(msg -> {
                    if (msg.isEvent()) {
                        counts.get(clientId).incrementAndGet();
                        totalLatch.countDown();
                    }
                });
                clients.add(c);
            }

            // Publish multiple events
            for (int e = 0; e < eventCount; e++) {
                inMemoryContext.getRecruitmentDriveService().createDrive(
                        "DRV-10SUB-" + e + "-" + System.currentTimeMillis(),
                        "CMP-002",
                        "Role " + e,
                        7.5,
                        Set.of("CSE", "IT"),
                        LocalDate.now().plusDays(15)
                );
            }

            assertTrue(totalLatch.await(8, TimeUnit.SECONDS), "All 10 subscribers must receive all 3 events");
            for (int i = 0; i < clientCount; i++) {
                assertEquals(eventCount, counts.get(i).get(), "Subscriber " + i + " must have received all events");
            }
        } finally {
            for (HireFlowTcpClient c : clients) {
                c.close();
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("3. No duplicate delivery after repeated SUBSCRIBE_EVENTS requests on same connection")
    void testNoDuplicateDeliveryOnRepeatedSubscribe() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        AtomicInteger deliveryCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
            client.connect();

            // First subscription
            ResponseMessage resp1 = client.subscribeEvents(msg -> {
                if (msg.isEvent()) {
                    deliveryCount.incrementAndGet();
                    latch.countDown();
                }
            });
            assertTrue(resp1.isOk());

            // Second subscription on same connection
            ResponseMessage resp2 = client.subscribeEvents(null);
            assertTrue(resp2.isOk());
            assertTrue(resp2.data().toString().contains("alreadySubscribed") || resp2.message().contains("Already subscribed"));

            // Publish event
            inMemoryContext.getRecruitmentDriveService().createDrive(
                    "DRV-NODUP-" + System.currentTimeMillis(),
                    "CMP-001", "Robotics Engineer", 8.2, Set.of("CSE"), LocalDate.now().plusDays(10)
            );

            assertTrue(latch.await(3, TimeUnit.SECONDS));
            Thread.sleep(150); // Small pause to ensure no second duplicate frame arrives
            assertEquals(1, deliveryCount.get(), "Must not receive duplicate deliveries for repeated subscription requests");
        }
    }

    @Test
    @Order(4)
    @DisplayName("4. Client disconnect and subscriber cleanup")
    void testClientDisconnectAndSubscriberCleanup() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort());
        client.connect();
        client.subscribeEvents(msg -> {});

        assertEquals(1, server.getEventBroadcaster().getSubscriberCount(), "Subscriber count must be 1");

        // Close client
        client.close();

        // Give server thread a moment to detect EOF and unregister
        long deadline = System.currentTimeMillis() + 2000;
        while (server.getEventBroadcaster().getSubscriberCount() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        assertEquals(0, server.getEventBroadcaster().getSubscriberCount(), "Subscriber must be cleaned up after disconnect");
    }

    @Test
    @Order(5)
    @DisplayName("5. Client reconnect and fresh subscription")
    void testClientReconnectAndFreshSubscription() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        // 1. Initial connect and disconnect
        HireFlowTcpClient c1 = new HireFlowTcpClient("127.0.0.1", server.getPort());
        c1.connect();
        c1.subscribeEvents(msg -> {});
        c1.close();

        Thread.sleep(100);

        // 2. Reconnect
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedId = new AtomicReference<>();

        try (HireFlowTcpClient c2 = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
            c2.connect();
            c2.subscribeEvents(msg -> {
                if (msg.isEvent()) {
                    receivedId.set((String) msg.getEventPayload().get("driveId"));
                    latch.countDown();
                }
            });

            String driveId = "DRV-RECONNECT-" + System.currentTimeMillis();
            inMemoryContext.getRecruitmentDriveService().createDrive(
                    driveId, "CMP-003", "FinTech Analyst", 8.5, Set.of("CSE", "IT"), LocalDate.now().plusDays(12)
            );

            assertTrue(latch.await(4, TimeUnit.SECONDS), "Reconnected client must receive event");
            assertEquals(driveId, receivedId.get());
        }
    }

    @Test
    @Order(6)
    @DisplayName("6. Subscription idle beyond the previous 10-second socket timeout")
    void testSubscriptionIdleBeyondTimeout() throws Exception {
        // Configure server with very short read timeout of 500ms
        server = new HireFlowTcpServer(inMemoryContext, "127.0.0.1", 0, 10, 500);
        server.start();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedId = new AtomicReference<>();

        try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort(), 2000, 2000)) {
            client.connect();
            client.subscribeEvents(msg -> {
                if (msg.isEvent()) {
                    receivedId.set((String) msg.getEventPayload().get("driveId"));
                    latch.countDown();
                }
            });

            // Stay completely idle for 1500ms (>3 cycles of 500ms timeout)
            Thread.sleep(1500);

            assertTrue(client.isConnected(), "Client socket must remain open and healthy across idle timeouts");

            // Publish event after idle period
            String driveId = "DRV-IDLE-AWAKE-" + System.currentTimeMillis();
            inMemoryContext.getRecruitmentDriveService().createDrive(
                    driveId, "CMP-001", "AI Researcher", 9.0, Set.of("CSE"), LocalDate.now().plusDays(20)
            );

            assertTrue(latch.await(3, TimeUnit.SECONDS), "Idle subscriber must receive event without disconnect");
            assertEquals(driveId, receivedId.get());
        }
    }

    @Test
    @Order(7)
    @DisplayName("7. Slow subscriber does not delay fast subscribers")
    void testSlowSubscriberDoesNotDelayFastSubscriber() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        CountDownLatch fastLatch = new CountDownLatch(1);
        long startBroadcast;

        // 1. Connect a slow/frozen raw socket that never reads incoming packets
        Socket slowSocket = new Socket("127.0.0.1", server.getPort());
        slowSocket.getOutputStream().write("{\"version\":\"1.0\",\"command\":\"SUBSCRIBE_EVENTS\"}\n".getBytes());
        slowSocket.getOutputStream().flush();
        // Give server a moment to register slow socket
        Thread.sleep(100);

        // 2. Connect a fast subscriber
        try (HireFlowTcpClient fastClient = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
            fastClient.connect();
            fastClient.subscribeEvents(msg -> {
                if (msg.isEvent()) {
                    fastLatch.countDown();
                }
            });

            startBroadcast = System.currentTimeMillis();
            inMemoryContext.getRecruitmentDriveService().createDrive(
                    "DRV-SLOW-FAST-" + System.currentTimeMillis(),
                    "CMP-001", "Fast Path Engineer", 8.0, Set.of("CSE"), LocalDate.now().plusDays(10)
            );

            // Fast client must receive the event almost instantly
            boolean received = fastLatch.await(1, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - startBroadcast;

            assertTrue(received, "Fast subscriber must receive event promptly despite slow subscriber");
            assertTrue(elapsed < 1000, "Event delivery to fast subscriber must not be blocked by slow client (took " + elapsed + " ms)");
        } finally {
            slowSocket.close();
        }
    }

    @Test
    @Order(8)
    @DisplayName("8. Per-subscriber event ordering")
    void testPerSubscriberEventOrdering() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        int count = 5;
        CountDownLatch latch = new CountDownLatch(count);
        List<String> receivedDriveIds = Collections.synchronizedList(new ArrayList<>());

        try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
            client.connect();
            client.subscribeEvents(msg -> {
                if (msg.isEvent()) {
                    receivedDriveIds.add((String) msg.getEventPayload().get("driveId"));
                    latch.countDown();
                }
            });

            List<String> expectedIds = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String dId = "DRV-ORDER-" + i + "-" + System.currentTimeMillis();
                expectedIds.add(dId);
                inMemoryContext.getRecruitmentDriveService().createDrive(
                        dId, "CMP-001", "Order Role " + i, 8.0, Set.of("CSE"), LocalDate.now().plusDays(10)
                );
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "Must receive all ordered events");
            assertEquals(expectedIds, receivedDriveIds, "Events must be received in strict FIFO creation order");
        }
    }

    @Test
    @Order(9)
    @DisplayName("9. Bounded queue overflow handling triggers overload policy")
    void testBoundedQueueOverflowHandling() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        // Connect raw socket that subscribes but never reads anything from its input stream
        Socket unresponsiveSocket = new Socket("127.0.0.1", server.getPort());
        unresponsiveSocket.getOutputStream().write("{\"version\":\"1.0\",\"command\":\"SUBSCRIBE_EVENTS\"}\n".getBytes());
        unresponsiveSocket.getOutputStream().flush();
        Thread.sleep(100);

        assertEquals(1, server.getEventBroadcaster().getSubscriberCount());

        // Emit enough events with payload size to saturate TCP send buffer and bounded queue (50 capacity)
        for (int i = 0; i < 250; i++) {
            inMemoryContext.getRecruitmentDriveService().createDrive(
                    "DRV-OVERLOAD-" + i + "-" + System.currentTimeMillis(),
                    "CMP-001",
                    "Overload Role With Extended Buffer Filling Payload " + "X".repeat(350) + " " + i,
                    7.0,
                    Set.of("CSE", "IT", "ECE"),
                    LocalDate.now().plusDays(10)
            );
        }

        // Give server worker thread a moment to execute the overload disconnect policy
        long deadline = System.currentTimeMillis() + 4000;
        while (server.getEventBroadcaster().getSubscriberCount() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        assertEquals(0, server.getEventBroadcaster().getSubscriberCount(),
                "Overloaded subscriber must be disconnected and removed from registry to protect memory");
        unresponsiveSocket.close();
    }

    @Test
    @Order(10)
    @DisplayName("10. Abrupt client socket termination handled without crashing server")
    void testAbruptClientSocketTermination() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        Socket rstSocket = new Socket("127.0.0.1", server.getPort());
        rstSocket.getOutputStream().write("{\"version\":\"1.0\",\"command\":\"SUBSCRIBE_EVENTS\"}\n".getBytes());
        rstSocket.getOutputStream().flush();
        Thread.sleep(100);

        // Abruptly terminate socket with RST
        rstSocket.setSoLinger(true, 0);
        rstSocket.close();

        // Broadcast event: server must not crash or throw unhandled exceptions
        assertDoesNotThrow(() -> {
            inMemoryContext.getRecruitmentDriveService().createDrive(
                    "DRV-RST-" + System.currentTimeMillis(),
                    "CMP-001", "RST Role", 8.0, Set.of("CSE"), LocalDate.now().plusDays(10)
            );
        });

        // Server must remain operational for subsequent clients
        try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
            client.connect();
            assertTrue(client.ping().isOk());
        }
    }

    @Test
    @Order(11)
    @DisplayName("11. Server shutdown cleans up all active subscriptions")
    void testServerShutdownCleansUpSubscriptions() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        List<HireFlowTcpClient> clients = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            HireFlowTcpClient c = new HireFlowTcpClient("127.0.0.1", server.getPort());
            c.connect();
            c.subscribeEvents(msg -> {});
            clients.add(c);
        }

        assertEquals(5, server.getEventBroadcaster().getSubscriberCount());

        server.stop();
        assertFalse(server.isRunning());
        assertEquals(0, server.getEventBroadcaster().getSubscriberCount(), "All subscriptions must be cleaned up on server stop");

        for (HireFlowTcpClient c : clients) {
            c.close();
        }
    }

    @Test
    @Order(12)
    @DisplayName("12. No leaked worker threads or sockets after shutdown")
    void testNoLeakedThreadsOrSockets() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        for (int i = 0; i < 4; i++) {
            try (HireFlowTcpClient c = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
                c.connect();
                c.subscribeEvents(msg -> {});
            }
        }

        server.stop();

        // Check active threads in JVM
        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        long subscriberThreads = threads.stream()
                .filter(t -> t.getName().startsWith("hireflow-subscriber-writer-") && t.isAlive())
                .count();
        assertEquals(0, subscriberThreads, "All subscriber writer threads must be stopped after server shutdown");
    }

    @Test
    @Order(13)
    @DisplayName("13. Non-public domain events rejected by privacy filtering")
    void testPrivacyFilteringRejectsNonPublicEvents() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        AtomicInteger eventCount = new AtomicInteger(0);

        try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
            client.connect();
            client.subscribeEvents(msg -> {
                if (msg.isEvent()) {
                    eventCount.incrementAndGet();
                }
            });

            // Trigger non-public private candidate event
            inMemoryContext.getStudentService().registerStudent(
                    "STU-PRIV-999", "Secret Candidate", "secret@univ.edu", "CSE", 9.99, 2026
            );

            // Trigger application submit
            try {
                inMemoryContext.getApplicationService().submitApplication("STU-PRIV-999", "DRV-101");
            } catch (Exception ignored) {
            }

            Thread.sleep(300);
            assertEquals(0, eventCount.get(), "Private student/application events must NEVER be broadcast to TCP subscribers");
        }
    }

    @Test
    @Order(14)
    @DisplayName("14. Event payload contains only approved public fields, no sensitive data")
    void testEventPayloadContainsNoSensitiveFields() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

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

            inMemoryContext.getRecruitmentDriveService().createDrive(
                    "DRV-SAFE-CHECK", "CMP-001", "Security Analyst", 8.0, Set.of("CSE"), LocalDate.now().plusDays(10)
            );

            assertTrue(latch.await(3, TimeUnit.SECONDS));
            ResponseMessage msg = received.get();
            String rawJson = msg.toJson().toLowerCase();

            // Assert absolute absence of student PII and confidential recruitment outcomes
            assertFalse(rawJson.contains("alice smith"));
            assertFalse(rawJson.contains("stu-101"));
            assertFalse(rawJson.contains("@univ.edu"));
            assertFalse(rawJson.contains("password"));
            assertFalse(rawJson.contains("secret"));
            assertFalse(rawJson.contains("interviewsession"));

            // Assert approved public fields
            Map<String, Object> payload = msg.getEventPayload();
            assertEquals("DRV-SAFE-CHECK", payload.get("driveId"));
            assertEquals("CMP-001", payload.get("companyId"));
            assertEquals("Google Cloud", payload.get("companyName"));
            assertEquals("Security Analyst", payload.get("jobTitle"));
        }
    }

    @Test
    @Order(15)
    @DisplayName("15. JDBC transaction rollback does not broadcast an announcement")
    void testJdbcTransactionRollbackDoesNotBroadcast() throws Exception {
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
            server = new HireFlowTcpServer(jdbcContext, 0);
            server.start();

            AtomicInteger eventCounter = new AtomicInteger(0);

            try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
                client.connect();
                client.subscribeEvents(msg -> {
                    if (msg.isEvent()) {
                        eventCounter.incrementAndGet();
                    }
                });

                TransactionManager txManager = jdbcContext.getTransactionManager().orElseThrow();

                // Execute transactional block that rolls back
                assertThrows(RuntimeException.class, () -> {
                    txManager.runInTransactionWithoutResult(conn -> {
                        jdbcContext.getRecruitmentDriveService().createDrive(
                                "DRV-ROLLBACK-" + System.currentTimeMillis(),
                                "CMP-001",
                                "Temporary Role",
                                8.0,
                                Set.of("CSE"),
                                LocalDate.now().plusDays(10)
                        );
                        throw new RuntimeException("Simulated transaction failure to trigger rollback");
                    });
                });

                Thread.sleep(400);
                assertEquals(0, eventCounter.get(),
                        "Rolled-back transaction must NOT emit or broadcast any recruitment announcement");
            }
        }
    }

    @Test
    @Order(16)
    @DisplayName("16. Successfully committed recruitment drive emits an event post-commit")
    void testCommittedRecruitmentDriveEmitsEvent() throws Exception {
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
            server = new HireFlowTcpServer(jdbcContext, 0);
            server.start();

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> committedId = new AtomicReference<>();

            try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
                client.connect();
                client.subscribeEvents(msg -> {
                    if (msg.isEvent()) {
                        committedId.set((String) msg.getEventPayload().get("driveId"));
                        latch.countDown();
                    }
                });

                TransactionManager txManager = jdbcContext.getTransactionManager().orElseThrow();
                String testDriveId = "DRV-TX-COMMIT-" + System.currentTimeMillis();

                // Ensure company exists
                jdbcContext.getCompanyRepository().save(new Company("CMP-TX-COMMIT", "Tx Commit Corp", "Software"));

                // Execute transactional block that commits successfully
                txManager.runInTransactionWithoutResult(conn -> {
                    jdbcContext.getRecruitmentDriveService().createDrive(
                            testDriveId,
                            "CMP-TX-COMMIT",
                            "Committed Role",
                            8.0,
                            Set.of("CSE"),
                            LocalDate.now().plusDays(10)
                    );
                });

                assertTrue(latch.await(4, TimeUnit.SECONDS), "Committed drive must emit public event post-commit");
                assertEquals(testDriveId, committedId.get());
            }
        }
    }

    @Test
    @Order(17)
    @DisplayName("17. Multiplexed socket preserves response and event frame integrity")
    void testMultiplexedSocketFrameIntegrity() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        CountDownLatch eventLatch = new CountDownLatch(1);

        try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
            client.connect();
            client.subscribeEvents(msg -> {
                if (msg.isEvent()) {
                    eventLatch.countDown();
                }
            });

            // Concurrently send synchronous PING, GET_DRIVES while event is pushed
            String dId = "DRV-MULTIPLEX-" + System.currentTimeMillis();
            inMemoryContext.getRecruitmentDriveService().createDrive(
                    dId, "CMP-001", "Concurrent Engineer", 8.0, Set.of("CSE"), LocalDate.now().plusDays(10)
            );

            ResponseMessage pingResp = client.ping();
            assertTrue(pingResp.isOk());
            assertEquals("PONG", pingResp.message());

            ResponseMessage drivesResp = client.getDrives();
            assertTrue(drivesResp.isOk());

            ResponseMessage hbResp = client.heartbeat();
            assertTrue(hbResp.isOk());
            assertEquals("HEARTBEAT_ACK", hbResp.message());

            assertTrue(eventLatch.await(3, TimeUnit.SECONDS), "Event push must be cleanly demultiplexed");

            // Test unsubscribe
            ResponseMessage unsubResp = client.unsubscribeEvents();
            assertTrue(unsubResp.isOk());
            assertTrue(unsubResp.data().toString().contains("subscribed=false"));
        }
    }

    @Test
    @Order(18)
    @DisplayName("18. Concurrent server activity under load")
    void testConcurrentServerActivityUnderLoad() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        int clientCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(clientCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(clientCount);
        AtomicInteger successCounter = new AtomicInteger(0);

        for (int i = 0; i < clientCount; i++) {
            pool.submit(() -> {
                try {
                    startLatch.await();
                    try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
                        client.connect();
                        for (int k = 0; k < 5; k++) {
                            ResponseMessage p = client.ping();
                            ResponseMessage d = client.getDrives();
                            ResponseMessage s = client.getServerInfo();
                            if (!p.isOk() || !d.isOk() || !s.isOk()) {
                                return;
                            }
                        }
                        successCounter.incrementAndGet();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "All concurrent clients must finish without deadlock");
        assertEquals(clientCount, successCounter.get(), "All clients under concurrent load must succeed");
        pool.shutdown();
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
