package com.hireflow.api.officer;

import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Student;
import com.hireflow.repository.*;
import com.hireflow.web.security.PasswordHasher;
import com.hireflow.web.security.UserAccount;
import com.hireflow.web.security.UserAccountRepository;
import com.hireflow.web.security.UserRole;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OfficerWorkflowRealHttpIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired
    private javax.sql.DataSource cleanupDataSource;


    @LocalServerPort
    private int port;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private DriveRepository driveRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private InterviewerRepository interviewerRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    private HttpClient httpClient;

    private static final String STU_1_ID = "STU-E2E-OFF-1";
    private static final String STU_2_ID = "STU-E2E-OFF-2";
    private static final String PASSWORD = "OfficerWorkflowSecurePass2026!";

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        cleanDatabase();

        // 1. Setup Student 1 (eligible)
        Student student1 = new Student(STU_1_ID, "Emma Watson", "emma@hireflow.test", "CSE", 9.10, 2026, true);
        studentRepository.add(student1);

        // 2. Setup Student 2 (eligible)
        Student student2 = new Student(STU_2_ID, "Tom Holland", "tom@hireflow.test", "CSE", 8.40, 2026, true);
        studentRepository.add(student2);

        // 3. Setup User Accounts
        String hash = passwordHasher.hashPassword(PASSWORD.toCharArray());

        UserAccount officerUser = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username("officer_e2e")
                .passwordHash(hash)
                .role(UserRole.PLACEMENT_OFFICER)
                .displayName("Officer Evelyn")
                .enabled(true)
                .build();
        userAccountRepository.save(officerUser);

        UserAccount student1User = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username("student1_e2e")
                .passwordHash(hash)
                .role(UserRole.STUDENT)
                .studentId(STU_1_ID)
                .displayName("Emma Watson")
                .enabled(true)
                .build();
        userAccountRepository.save(student1User);

        UserAccount student2User = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username("student2_e2e")
                .passwordHash(hash)
                .role(UserRole.STUDENT)
                .studentId(STU_2_ID)
                .displayName("Tom Holland")
                .enabled(true)
                .build();
        userAccountRepository.save(student2User);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        com.hireflow.api.testsupport.SafeTestDatabase.clean(cleanupDataSource,
            new String[]{"STU-E2E-OFF-1", "STU-E2E-OFF-2", "DRV-E2E-99", "INT-E2E-99", "RM-E2E-99", "CMP-E2E-99"},
            new String[]{"officer_e2e", "student1_e2e", "student2_e2e"});
    }

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    private String extractSessionId(HttpResponse<?> response) {
        List<String> cookies = response.headers().allValues("Set-Cookie");
        for (String cookie : cookies) {
            if (cookie.contains("JSESSIONID=")) {
                int start = cookie.indexOf("JSESSIONID=") + "JSESSIONID=".length();
                int end = cookie.indexOf(';', start);
                return (end > 0) ? cookie.substring(start, end) : cookie.substring(start);
            }
        }
        return null;
    }

    private record ClientSession(String sessionId, String csrfToken, String csrfHeader) {}

    private ClientSession login(String username, String password) throws Exception {
        // 1. Fetch CSRF token
        HttpResponse<String> csrfRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/csrf"))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, csrfRes.statusCode());

        String initialSid = extractSessionId(csrfRes);
        assertNotNull(initialSid);
        String csrfToken = JsonPath.read(csrfRes.body(), "$.token");
        String csrfHeader = JsonPath.read(csrfRes.body(), "$.headerName");

        // 2. Login
        String payload = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        HttpResponse<String> loginRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + initialSid)
                .header(csrfHeader, csrfToken)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loginRes.statusCode());

        String rotatedSid = extractSessionId(loginRes);
        assertNotNull(rotatedSid);

        // 3. Post-auth CSRF token
        HttpResponse<String> postAuthCsrfRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/auth/csrf"))
                .header("Cookie", "JSESSIONID=" + rotatedSid)
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        String postAuthCsrf = JsonPath.read(postAuthCsrfRes.body(), "$.token");

        return new ClientSession(rotatedSid, postAuthCsrf, csrfHeader);
    }

    @Test
    @DisplayName("Stage 4D Primary: End-to-End Officer Company/Drive Lifecycle, Student Application, Review, Shortlist & Drive Closure")
    void testEndToEndOfficerLifecycle() throws Exception {
        // 1. Officer logs in
        ClientSession officer = login("officer_e2e", PASSWORD);

        // 2. Officer registers company CMP-E2E-99
        String createCompanyJson = """
                {
                    "id": "CMP-E2E-99",
                    "name": "Starlight Robotics Corp",
                    "industry": "Autonomous Vehicles"
                }
                """;
        HttpResponse<String> createCompRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/officer/companies"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + officer.sessionId())
                .header(officer.csrfHeader(), officer.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString(createCompanyJson))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createCompRes.statusCode());
        assertEquals("CMP-E2E-99", JsonPath.read(createCompRes.body(), "$.id"));

        // 3. Officer creates recruitment drive DRV-E2E-99
        String createDriveJson = """
                {
                    "id": "DRV-E2E-99",
                    "companyId": "CMP-E2E-99",
                    "jobTitle": "Perception Algorithms Engineer",
                    "minimumCgpa": 8.0,
                    "eligibleBranches": ["CSE"],
                    "applicationDeadline": "%s"
                }
                """.formatted(LocalDate.now().plusMonths(2));
        HttpResponse<String> createDriveRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/officer/drives"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + officer.sessionId())
                .header(officer.csrfHeader(), officer.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString(createDriveJson))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createDriveRes.statusCode());
        assertEquals("DRV-E2E-99", JsonPath.read(createDriveRes.body(), "$.id"));

        // 4. Student 1 logs in in a separate session
        ClientSession student1 = login("student1_e2e", PASSWORD);

        // 5. Student 1 inspects drives and sees DRV-E2E-99
        HttpResponse<String> studentDrivesRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/student/drives"))
                .header("Cookie", "JSESSIONID=" + student1.sessionId())
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, studentDrivesRes.statusCode());
        List<String> driveIds = JsonPath.read(studentDrivesRes.body(), "$[*].id");
        assertTrue(driveIds.contains("DRV-E2E-99"));

        // 6. Student 1 applies to DRV-E2E-99
        String applyJson = """
                {
                    "driveId": "DRV-E2E-99"
                }
                """;
        HttpResponse<String> applyRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/student/applications"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + student1.sessionId())
                .header(student1.csrfHeader(), student1.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString(applyJson))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, applyRes.statusCode());
        String submittedAppId = JsonPath.read(applyRes.body(), "$.id");
        assertNotNull(submittedAppId);
        assertEquals("APPLIED", JsonPath.read(applyRes.body(), "$.status"));

        // 7. Officer views candidate applications for DRV-E2E-99
        HttpResponse<String> appsRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/officer/drives/DRV-E2E-99/applications"))
                .header("Cookie", "JSESSIONID=" + officer.sessionId())
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, appsRes.statusCode());
        assertEquals(1, (int) JsonPath.read(appsRes.body(), "$.totalElements"));
        assertEquals("Emma Watson", JsonPath.read(appsRes.body(), "$.content[0].studentName"));
        assertEquals(9.10, JsonPath.read(appsRes.body(), "$.content[0].cgpa"));

        // 8. Officer shortlists the candidate
        HttpResponse<String> shortlistRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/officer/applications/" + submittedAppId + "/shortlist"))
                .header("Cookie", "JSESSIONID=" + officer.sessionId())
                .header(officer.csrfHeader(), officer.csrfToken())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, shortlistRes.statusCode());
        assertEquals("SHORTLISTED", JsonPath.read(shortlistRes.body(), "$.status"));

        // 9. Student 1 retrieves application and observes SHORTLISTED status
        HttpResponse<String> checkAppRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/student/applications/" + submittedAppId))
                .header("Cookie", "JSESSIONID=" + student1.sessionId())
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, checkAppRes.statusCode());
        assertEquals("SHORTLISTED", JsonPath.read(checkAppRes.body(), "$.status"));

        // 10. Officer closes the recruitment drive
        HttpResponse<String> closeRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/officer/drives/DRV-E2E-99/close"))
                .header("Cookie", "JSESSIONID=" + officer.sessionId())
                .header(officer.csrfHeader(), officer.csrfToken())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, closeRes.statusCode());
        assertTrue((Boolean) JsonPath.read(closeRes.body(), "$.closed"));

        // 11. Student 2 logs in and attempts to submit application to closed drive -> 400 Bad Request
        ClientSession student2 = login("student2_e2e", PASSWORD);
        HttpResponse<String> lateApplyRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/student/applications"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + student2.sessionId())
                .header(student2.csrfHeader(), student2.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString(applyJson))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(400, lateApplyRes.statusCode());
    }

    @Test
    @DisplayName("Officer Security Controls: Role isolation, CSRF rejection, and Interview Resource creation")
    void testOfficerSecurityAndResourceOperations() throws Exception {
        ClientSession officer = login("officer_e2e", PASSWORD);
        ClientSession student = login("student1_e2e", PASSWORD);

        // 1. Student attempting officer route returns 403 Forbidden
        HttpResponse<String> studentAttempt = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/officer/companies"))
                .header("Cookie", "JSESSIONID=" + student.sessionId())
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, studentAttempt.statusCode());

        // 2. Missing CSRF on mutating officer route returns 403 Forbidden
        String createCompJson = """
                {
                    "id": "CMP-E2E-99",
                    "name": "Starlight Robotics Corp",
                    "industry": "Autonomous Vehicles"
                }
                """;
        HttpResponse<String> noCsrfAttempt = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/officer/companies"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + officer.sessionId())
                .POST(HttpRequest.BodyPublishers.ofString(createCompJson))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, noCsrfAttempt.statusCode());

        // 3. Officer creates interview room
        String createRoomJson = """
                {
                    "id": "RM-E2E-99",
                    "name": "Executive Boardroom",
                    "capacity": 12
                }
                """;
        HttpResponse<String> createRoomRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/officer/rooms"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + officer.sessionId())
                .header(officer.csrfHeader(), officer.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString(createRoomJson))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createRoomRes.statusCode());
        assertEquals("RM-E2E-99", JsonPath.read(createRoomRes.body(), "$.id"));
    }
}
