package com.hireflow.network;

import com.hireflow.context.ApplicationContext;
import com.hireflow.db.DatabaseConfig;
import com.hireflow.db.DatabaseMode;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.network.client.HireFlowTcpClient;
import com.hireflow.network.protocol.NetworkCommand;
import com.hireflow.network.protocol.RequestMessage;
import com.hireflow.network.protocol.ResponseMessage;
import com.hireflow.network.server.HireFlowTcpServer;
import com.hireflow.service.SampleDataLoader;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.Socket;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("HireFlow TCP Server & Client Integration Tests")
public class HireFlowTcpServerTest {

    private static ApplicationContext inMemoryContext;
    private HireFlowTcpServer server;

    @BeforeAll
    static void initGlobalContext() {
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
    void tearDownServer() {
        if (server != null && server.isRunning()) {
            server.stop();
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. Successful server startup on dynamic port")
    void testServerStartup() throws IOException {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        assertTrue(server.isRunning(), "Server must be running after start()");
        assertTrue(server.getPort() > 0, "Server must have a non-zero ephemeral port");
        assertEquals("127.0.0.1", server.getBindHost());
    }

    @Test
    @Order(2)
    @DisplayName("2. PING/PONG connectivity check")
    void testPingPong() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
            client.connect();
            ResponseMessage response = client.ping();

            assertNotNull(response);
            assertTrue(response.isOk());
            assertEquals(200, response.code());
            assertEquals("PONG", response.message());
            assertTrue(response.data() instanceof Map<?, ?>);
            Map<?, ?> map = (Map<?, ?>) response.data();
            assertEquals("ONLINE", map.get("serverStatus"));
            assertNotNull(map.get("timestamp"));
        }
    }

    @Test
    @Order(3)
    @DisplayName("3. SERVER_INFO capability and version response")
    void testServerInfo() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
            client.connect();
            ResponseMessage response = client.getServerInfo();

            assertTrue(response.isOk());
            assertEquals(200, response.code());
            assertTrue(response.data() instanceof Map<?, ?>);
            Map<?, ?> data = (Map<?, ?>) response.data();

            assertEquals("HireFlow Placement Management Server", data.get("serverName"));
            assertEquals("1.0.0-stage2", data.get("serverVersion"));
            assertEquals("1.0", data.get("protocolVersion"));
            assertEquals("IN_MEMORY", data.get("storageMode"));
            assertTrue(data.get("supportedCommands") instanceof List<?>);
            List<?> cmds = (List<?>) data.get("supportedCommands");
            assertTrue(cmds.contains("PING"));
            assertTrue(cmds.contains("SERVER_INFO"));
            assertTrue(cmds.contains("GET_DRIVES"));
            assertTrue(cmds.contains("SUBSCRIBE_EVENTS"));
        }
    }

    @Test
    @Order(4)
    @DisplayName("4. GET_DRIVES public recruitment drives catalog")
    void testGetDrives() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
            client.connect();
            ResponseMessage response = client.getDrives();

            assertTrue(response.isOk());
            assertEquals(200, response.code());
            assertTrue(response.data() instanceof Map<?, ?>);
            Map<?, ?> data = (Map<?, ?>) response.data();

            assertTrue(data.get("drives") instanceof List<?>);
            List<?> drivesList = (List<?>) data.get("drives");
            assertFalse(drivesList.isEmpty(), "Drives catalog must not be empty");

            // Verify safe attributes in first drive
            Map<?, ?> first = (Map<?, ?>) drivesList.get(0);
            assertNotNull(first.get("driveId"));
            assertNotNull(first.get("companyName"));
            assertNotNull(first.get("jobTitle"));
            assertNotNull(first.get("minimumCgpa"));
            assertNotNull(first.get("eligibleBranches"));
            assertNotNull(first.get("applicationDeadline"));
            assertNotNull(first.get("active"));
        }
    }

    @Test
    @Order(5)
    @DisplayName("5. Multiple simultaneous concurrent clients")
    void testMultipleSimultaneousClients() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        int clientCount = 10;
        ExecutorService clientPool = Executors.newFixedThreadPool(clientCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(clientCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < clientCount; i++) {
            final int id = i;
            clientPool.submit(() -> {
                try {
                    startLatch.await();
                    try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
                        client.connect();
                        ResponseMessage r1 = client.ping();
                        ResponseMessage r2 = client.getDrives();
                        if (r1.isOk() && r2.isOk()) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "All clients must complete within 10 seconds");
        assertEquals(clientCount, successCount.get(), "All concurrent clients must succeed");
        clientPool.shutdown();
    }

    @Test
    @Order(6)
    @DisplayName("6. Invalid command rejection")
    void testInvalidCommandRejection() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
            client.connect();
            RequestMessage badReq = RequestMessage.of("DELETE_ALL_DATA", "req-bad");
            ResponseMessage response = client.sendRequest(badReq);

            assertFalse(response.isOk());
            assertEquals(400, response.code());
            assertEquals("ERROR", response.status());
            assertTrue(response.message().contains("Unknown command"));
        }
    }

    @Test
    @Order(7)
    @DisplayName("7. Malformed protocol message rejection")
    void testMalformedProtocolMessage() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
            client.connect();
            String rawResponse = client.sendRaw("{ not_valid_json }");
            assertNotNull(rawResponse);
            assertTrue(rawResponse.contains("\"code\":400") || rawResponse.contains("Malformed"));

            // Server must remain healthy for subsequent valid request
            ResponseMessage validPing = client.ping();
            assertTrue(validPing.isOk());
        }
    }

    @Test
    @Order(8)
    @DisplayName("8. Oversized message rejection")
    void testOversizedMessageRejection() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
            client.connect();
            // Send payload exceeding 64KB
            String hugeLine = "{\"command\":\"PING\",\"payload\":\"" + "X".repeat(70000) + "\"}";
            String rawResponse = client.sendRaw(hugeLine);

            assertNotNull(rawResponse);
            assertTrue(rawResponse.contains("413") || rawResponse.contains("exceeds maximum allowed length"));
        }

        // Verify server remains fully functional for new clients
        try (HireFlowTcpClient freshClient = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
            freshClient.connect();
            ResponseMessage ping = freshClient.ping();
            assertTrue(ping.isOk());
        }
    }

    @Test
    @Order(9)
    @DisplayName("9. Client disconnect handling")
    void testClientDisconnectHandling() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        Socket directSocket = new Socket("127.0.0.1", server.getPort());
        directSocket.getOutputStream().write("{\"version\":\"1.0\",\"command\":\"PING\"}\n".getBytes());
        directSocket.getOutputStream().flush();

        // Abruptly close socket from client side
        directSocket.close();

        // Give server thread a moment to notice EOF
        Thread.sleep(100);
        assertEquals(0, server.getActiveClientCount(), "Active client count must drop to 0 after disconnect");
    }

    @Test
    @Order(10)
    @DisplayName("10. Socket read timeout handling on idle client")
    void testSocketReadTimeoutHandling() throws Exception {
        // Start server with short socket timeout of 1000ms
        server = new HireFlowTcpServer(inMemoryContext, "127.0.0.1", 0, 10, 1000);
        server.start();

        try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort(), 2000, 3000)) {
            client.connect();
            // Do not send anything; wait for server timeout response (code 408)
            ResponseMessage timeoutResp = client.readNextResponse();
            assertNotNull(timeoutResp);
            assertEquals(408, timeoutResp.code());
            assertTrue(timeoutResp.message().contains("Read timeout"));
        }
    }

    @Test
    @Order(11)
    @DisplayName("11. Server graceful shutdown")
    void testServerGracefulShutdown() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();
        int boundPort = server.getPort();

        HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", boundPort);
        client.connect();
        assertTrue(client.ping().isOk());

        server.stop();
        assertFalse(server.isRunning(), "Server must no longer be running");
        assertEquals(0, server.getActiveClientCount());

        // Subsequent connection attempts must fail
        assertThrows(IOException.class, () -> {
            try (HireFlowTcpClient secondClient = new HireFlowTcpClient("127.0.0.1", boundPort)) {
                secondClient.connect();
            }
        });
        client.close();
    }

    @Test
    @Order(12)
    @DisplayName("12. No leaked worker threads or sockets after shutdown")
    void testNoLeakedThreadsOrSockets() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        for (int i = 0; i < 5; i++) {
            try (HireFlowTcpClient c = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
                c.connect();
                c.ping();
            }
        }

        server.stop();

        // Check active threads in JVM
        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        long activeWorkers = threads.stream()
                .filter(t -> t.getName().startsWith("hireflow-tcp-worker-") && t.isAlive())
                .count();
        assertEquals(0, activeWorkers, "Worker threads must be terminated after server stop");
    }

    @Test
    @Order(13)
    @DisplayName("13. In-memory mode networking")
    void testInMemoryModeNetworking() throws Exception {
        ApplicationContext memCtx = ApplicationContext.createInMemory();
        memCtx.getCompanyRepository().save(new com.hireflow.model.Company("CMP-INMEM", "InMemory Tech", "Software"));
        memCtx.getDriveRepository().save(new RecruitmentDrive(
                "DRV-INMEM", "CMP-INMEM", "In-Memory Engineer", 8.0, Set.of("CSE"), LocalDate.now().plusDays(10)
        ));

        server = new HireFlowTcpServer(memCtx, 0);
        server.start();

        try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
            client.connect();
            ResponseMessage resp = client.getDrives();
            assertTrue(resp.isOk());

            Map<?, ?> data = (Map<?, ?>) resp.data();
            List<?> drives = (List<?>) data.get("drives");
            boolean found = drives.stream()
                    .filter(d -> d instanceof Map<?, ?>)
                    .map(d -> (Map<?, ?>) d)
                    .anyMatch(d -> "DRV-INMEM".equals(d.get("driveId")));
            assertTrue(found, "InMemory recruitment drive must be accessible via TCP server");
        }
    }

    @Test
    @Order(14)
    @DisplayName("14. JDBC-backed read behavior")
    void testJdbcBackedNetworking() throws Exception {
        System.setProperty("hireflow.allow.test.context", "true");

        Properties localProps = new Properties();
        java.io.File localFile = new java.io.File("hireflow-local.properties");
        if (localFile.exists()) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(localFile)) {
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

        DatabaseConfig config = DatabaseConfig.builder()
                .mode(DatabaseMode.JDBC_TEST)
                .jdbcUrl(url)
                .username(user)
                .password(pass)
                .maximumPoolSize(5)
                .minimumIdle(1)
                .connectionTimeoutMs(5000)
                .build();

        ApplicationContext jdbcContext;
        try {
            jdbcContext = ApplicationContext.createJdbcTest(config);
        } catch (Exception ex) {
            // If postgres is unavailable, skip gently
            System.err.println("Skipping JDBC networking test due to DB connection failure: " + ex.getMessage());
            return;
        }

        try (jdbcContext) {
            // Seed a test drive directly in PostgreSQL
            long timestamp = System.currentTimeMillis();
            String testCompId = "CMP-NET-JDBC-" + timestamp;
            String testDriveId = "DRV-NET-JDBC-" + timestamp;
            jdbcContext.getCompanyRepository().save(new com.hireflow.model.Company(testCompId, "Postgres Cloud " + timestamp, "Cloud"));
            jdbcContext.getDriveRepository().save(new RecruitmentDrive(
                    testDriveId, testCompId, "PostgreSQL Distributed Engineer", 8.5, Set.of("CSE", "IT"), LocalDate.now().plusDays(15)
            ));

            server = new HireFlowTcpServer(jdbcContext, 0);
            server.start();

            try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
                client.connect();
                ResponseMessage info = client.getServerInfo();
                assertTrue(info.isOk());
                Map<?, ?> infoData = (Map<?, ?>) info.data();
                assertEquals("JDBC_TEST", infoData.get("storageMode"));

                ResponseMessage drivesResp = client.getDrives();
                assertTrue(drivesResp.isOk());
                Map<?, ?> data = (Map<?, ?>) drivesResp.data();
                List<?> drives = (List<?>) data.get("drives");
                boolean found = drives.stream()
                        .filter(d -> d instanceof Map<?, ?>)
                        .map(d -> (Map<?, ?>) d)
                        .anyMatch(d -> testDriveId.equals(d.get("driveId")));
                assertTrue(found, "Database-backed drive must be served over TCP");
            }
        }
    }

    @Test
    @Order(15)
    @DisplayName("15. No sensitive student information in unauthenticated responses")
    void testNoSensitiveInformationInResponses() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
            client.connect();

            // 1. GET_DRIVES inspection
            ResponseMessage drivesResp = client.getDrives();
            String drivesJson = drivesResp.toJson().toLowerCase();

            assertFalse(drivesJson.contains("alice smith"), "Must not expose student names");
            assertFalse(drivesJson.contains("stu-101"), "Must not expose student IDs");
            assertFalse(drivesJson.contains("@univ.edu"), "Must not expose student emails");
            assertFalse(drivesJson.contains("password"), "Must not contain passwords");
            assertFalse(drivesJson.contains("cgpa\":9."), "Must not expose candidate student CGPA");
            assertFalse(drivesJson.contains("shortlisted"), "Must not expose application status");

            // 2. SERVER_INFO inspection
            ResponseMessage infoResp = client.getServerInfo();
            String infoJson = infoResp.toJson().toLowerCase();
            assertFalse(infoJson.contains("password"), "Server info must not contain passwords");
            assertFalse(infoJson.contains("secret"), "Server info must not contain secrets");
        }
    }

    @Test
    @Order(16)
    @DisplayName("16. Asynchronous public recruitment announcement event dispatch")
    void testEventSubscriptionAndDispatch() throws Exception {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        server.start();

        CountDownLatch eventLatch = new CountDownLatch(1);
        AtomicReference<ResponseMessage> receivedEvent = new AtomicReference<>();

        try (HireFlowTcpClient client = new HireFlowTcpClient("127.0.0.1", server.getPort())) {
            client.connect();
            ResponseMessage subResp = client.subscribeEvents(event -> {
                receivedEvent.set(event);
                eventLatch.countDown();
            });
            assertTrue(subResp.isOk());

            // Create a drive via the recruitment drive service to trigger domain event
            String newDriveId = "DRV-EVENT-" + System.currentTimeMillis();
            inMemoryContext.getRecruitmentDriveService().createDrive(
                    newDriveId,
                    "CMP-001",
                    "Senior Distributed Systems Engineer",
                    8.8,
                    Set.of("CSE"),
                    LocalDate.now().plusDays(20)
            );

            assertTrue(eventLatch.await(5, TimeUnit.SECONDS), "Subscribed client must receive asynchronous event push");
            ResponseMessage push = receivedEvent.get();
            assertNotNull(push);
            assertTrue(push.isEvent());
            assertTrue(push.message().contains("Placement drive created") || push.message().contains("Distributed Systems"));
        }
    }

    @Test
    @Order(17)
    @DisplayName("17. NetworkDemoClient console demonstration executes cleanly")
    void testNetworkDemoClientMain() {
        server = new HireFlowTcpServer(inMemoryContext, 0);
        assertDoesNotThrow(() -> server.start());

        assertDoesNotThrow(() -> {
            com.hireflow.network.client.NetworkDemoClient.main(new String[]{String.valueOf(server.getPort()), "127.0.0.1"});
        });
    }
}
