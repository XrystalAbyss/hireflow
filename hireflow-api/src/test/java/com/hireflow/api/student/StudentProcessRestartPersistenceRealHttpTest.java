package com.hireflow.api.student;

import com.hireflow.api.HireFlowApiApplication;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Student;
import com.hireflow.repository.ApplicationRepository;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.repository.DriveRepository;
import com.hireflow.repository.StudentRepository;
import com.hireflow.web.security.PasswordHasher;
import com.hireflow.web.security.UserAccount;
import com.hireflow.web.security.UserAccountRepository;
import com.hireflow.web.security.UserRole;
import com.jayway.jsonpath.JsonPath;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real Process-Restart Persistence Integration Test (Part 7):
 * 1. Starts Spring Boot application instance against isolated hireflow_test database.
 * 2. Authenticates disposable student account over real HTTP.
 * 3. Submits an application through actual HTTP and confirms HTTP 201 Created.
 * 4. Stops the running Spring Boot server context.
 * 5. Confirms that its HikariCP connection pool is completely closed.
 * 6. Starts a completely fresh Spring Boot application instance (new port, new context, new pool).
 * 7. Authenticates the same student account on the fresh instance.
 * 8. Retrieves the previously created application over HTTP.
 * 9. Verifies persisted identifier, drive association, and status.
 * 10. Confirms in PostgreSQL that no duplicate rows exist.
 * 11. Cleans up disposable test fixtures without modifying hireflow_dev.
 */
class StudentProcessRestartPersistenceRealHttpTest {

    private static final String STU_ID = "STU-RESTART-01";
    private static final String USERNAME = "restart_student_user";
    private static final String PASSWORD = "RestartStudentPass2026!";
    private static final String CMP_ID = "CMP-RESTART-01";
    private static final String DRV_ID = "DRV-RESTART-01";

    @Test
    @DisplayName("Part 7: Real process-restart persistence across distinct Spring Boot server lifecycles")
    void testProcessRestartPersistence() throws Exception {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        String createdAppId = null;

        // =========================================================================
        // LIFECYCLE 1: Start Server Instance 1, Setup Data, Authenticate, and Submit
        // =========================================================================
        ConfigurableApplicationContext ctx1 = null;
        HikariDataSource ds1 = null;
        try {
            SpringApplication app1 = new SpringApplication(HireFlowApiApplication.class);
            ctx1 = app1.run("--server.port=0", "--spring.main.banner-mode=off");
            int port1 = ctx1.getEnvironment().getProperty("local.server.port", Integer.class);
            ds1 = ctx1.getBean(HikariDataSource.class);
            assertFalse(ds1.isClosed(), "HikariCP pool 1 must be active");

            // Setup fixtures via instance 1 repositories
            StudentRepository stuRepo = ctx1.getBean(StudentRepository.class);
            DriveRepository drvRepo = ctx1.getBean(DriveRepository.class);
            CompanyRepository cmpRepo = ctx1.getBean(CompanyRepository.class);
            UserAccountRepository uaRepo = ctx1.getBean(UserAccountRepository.class);
            PasswordHasher hasher = ctx1.getBean(PasswordHasher.class);

            // Clean any stale test rows
            cleanTestRecords(ctx1);

            stuRepo.add(new Student(STU_ID, "Restart Student", "restart@hireflow.test", "CSE", 8.80, 2026, true));
            cmpRepo.add(new Company(CMP_ID, "Restart Corp", "Enterprise Tech"));
            drvRepo.add(new RecruitmentDrive(DRV_ID, CMP_ID, "Cloud Architect", 7.50, Set.of("CSE"), LocalDate.now().plusMonths(2), false));

            String hash = hasher.hashPassword(PASSWORD.toCharArray());
            UserAccount account = UserAccount.builder()
                    .id(UUID.randomUUID().toString())
                    .username(USERNAME)
                    .passwordHash(hash)
                    .role(UserRole.STUDENT)
                    .studentId(STU_ID)
                    .displayName("Restart Candidate")
                    .enabled(true)
                    .build();
            uaRepo.save(account);

            // 1. Fetch CSRF token on instance 1
            HttpRequest csrfReq1 = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port1 + "/api/v1/auth/csrf"))
                    .GET()
                    .build();
            HttpResponse<String> csrfResp1 = httpClient.send(csrfReq1, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, csrfResp1.statusCode());
            String token1 = JsonPath.read(csrfResp1.body(), "$.token");
            String cookie1 = extractCookie(csrfResp1);

            // 2. Login on instance 1
            String loginBody = "{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}";
            HttpRequest loginReq1 = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port1 + "/api/v1/auth/login"))
                    .header("Content-Type", "application/json")
                    .header("X-CSRF-TOKEN", token1)
                    .header("Cookie", cookie1)
                    .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                    .build();
            HttpResponse<String> loginResp1 = httpClient.send(loginReq1, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, loginResp1.statusCode());
            String authCookie1 = extractCookie(loginResp1);

            // Fetch post-auth CSRF token for the rotated authenticated session
            HttpRequest postCsrfReq1 = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port1 + "/api/v1/auth/csrf"))
                    .header("Cookie", authCookie1)
                    .GET()
                    .build();
            HttpResponse<String> postCsrfResp1 = httpClient.send(postCsrfReq1, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, postCsrfResp1.statusCode());
            String authCsrf1 = JsonPath.read(postCsrfResp1.body(), "$.token");

            // 3. Submit application on instance 1
            String submitBody = "{\"driveId\":\"" + DRV_ID + "\"}";
            HttpRequest submitReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port1 + "/api/v1/student/applications"))
                    .header("Content-Type", "application/json")
                    .header("X-CSRF-TOKEN", authCsrf1)
                    .header("Cookie", authCookie1)
                    .POST(HttpRequest.BodyPublishers.ofString(submitBody))
                    .build();
            HttpResponse<String> submitResp = httpClient.send(submitReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, submitResp.statusCode(), "Application creation must return HTTP 201 Created");
            createdAppId = JsonPath.read(submitResp.body(), "$.id");
            assertNotNull(createdAppId);
            assertTrue(createdAppId.startsWith("APP-"));

        } finally {
            // =========================================================================
            // TEARDOWN LIFECYCLE 1: Stop Server Instance 1 and Confirm Pool is Closed
            // =========================================================================
            if (ctx1 != null) {
                ctx1.close();
            }
            if (ds1 != null) {
                assertTrue(ds1.isClosed(), "HikariCP pool 1 must be closed after context shutdown");
            }
        }

        // =========================================================================
        // LIFECYCLE 2: Start Fresh Server Instance 2, Authenticate, and Retrieve
        // =========================================================================
        ConfigurableApplicationContext ctx2 = null;
        HikariDataSource ds2 = null;
        try {
            SpringApplication app2 = new SpringApplication(HireFlowApiApplication.class);
            ctx2 = app2.run("--server.port=0", "--spring.main.banner-mode=off");
            int port2 = ctx2.getEnvironment().getProperty("local.server.port", Integer.class);
            ds2 = ctx2.getBean(HikariDataSource.class);
            assertFalse(ds2.isClosed(), "HikariCP pool 2 must be active on fresh server instance");

            // 4. Fetch fresh CSRF token on instance 2
            HttpRequest csrfReq2 = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port2 + "/api/v1/auth/csrf"))
                    .GET()
                    .build();
            HttpResponse<String> csrfResp2 = httpClient.send(csrfReq2, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, csrfResp2.statusCode());
            String token2 = JsonPath.read(csrfResp2.body(), "$.token");
            String cookie2 = extractCookie(csrfResp2);

            // 5. Login on instance 2
            String loginBody = "{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}";
            HttpRequest loginReq2 = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port2 + "/api/v1/auth/login"))
                    .header("Content-Type", "application/json")
                    .header("X-CSRF-TOKEN", token2)
                    .header("Cookie", cookie2)
                    .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                    .build();
            HttpResponse<String> loginResp2 = httpClient.send(loginReq2, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, loginResp2.statusCode());
            String authCookie2 = extractCookie(loginResp2);

            // 6. Retrieve application created in lifecycle 1 via fresh HTTP request on instance 2
            HttpRequest getAppReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port2 + "/api/v1/student/applications/" + createdAppId))
                    .header("Cookie", authCookie2)
                    .GET()
                    .build();
            HttpResponse<String> getAppResp = httpClient.send(getAppReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, getAppResp.statusCode(), "Persisted application must be retrievable on restarted server");

            assertEquals(createdAppId, JsonPath.read(getAppResp.body(), "$.id"));
            assertEquals(STU_ID, JsonPath.read(getAppResp.body(), "$.studentId"));
            assertEquals(DRV_ID, JsonPath.read(getAppResp.body(), "$.driveId"));
            assertEquals("APPLIED", JsonPath.read(getAppResp.body(), "$.status"));
            assertEquals("Cloud Architect", JsonPath.read(getAppResp.body(), "$.jobTitle"));
            assertEquals("Restart Corp", JsonPath.read(getAppResp.body(), "$.companyName"));

            // 7. Verify exactly 1 application exists in database (zero duplicate rows)
            ApplicationRepository appRepo2 = ctx2.getBean(ApplicationRepository.class);
            List<com.hireflow.model.Application> apps = appRepo2.findByStudentId(STU_ID);
            assertEquals(1, apps.size(), "Database must contain exactly 1 application for student");
            assertEquals(createdAppId, apps.get(0).getId());

            // Clean up test records
            cleanTestRecords(ctx2);

        } finally {
            if (ctx2 != null) {
                ctx2.close();
            }
            if (ds2 != null) {
                assertTrue(ds2.isClosed(), "HikariCP pool 2 must be closed after test completion");
            }
        }
    }

    private void cleanTestRecords(ConfigurableApplicationContext ctx) {
        try {
            ApplicationRepository appRepo = ctx.getBean(ApplicationRepository.class);
            StudentRepository stuRepo = ctx.getBean(StudentRepository.class);
            DriveRepository drvRepo = ctx.getBean(DriveRepository.class);
            CompanyRepository cmpRepo = ctx.getBean(CompanyRepository.class);
            UserAccountRepository uaRepo = ctx.getBean(UserAccountRepository.class);

            List<com.hireflow.model.Application> apps = appRepo.findByStudentId(STU_ID);
            for (com.hireflow.model.Application app : apps) {
                appRepo.deleteById(app.getId());
            }
            uaRepo.findByUsername(USERNAME).ifPresent(acc -> uaRepo.deleteById(acc.getId()));
            drvRepo.deleteById(DRV_ID);
            cmpRepo.deleteById(CMP_ID);
            stuRepo.deleteById(STU_ID);
        } catch (Exception ignored) {
        }
    }

    private String extractCookie(HttpResponse<?> response) {
        return response.headers().firstValue("Set-Cookie")
                .map(c -> c.split(";")[0])
                .orElse("");
    }
}
