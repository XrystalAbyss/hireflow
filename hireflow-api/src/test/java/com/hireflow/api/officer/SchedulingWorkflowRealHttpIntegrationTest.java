package com.hireflow.api.officer;

import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Student;
import com.hireflow.repository.*;
import com.hireflow.service.InterviewService;
import com.hireflow.service.event.HireFlowEvent;
import com.hireflow.service.event.HireFlowEventListener;
import com.hireflow.service.event.HireFlowEventType;
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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("SchedulingWorkflowRealHttpIntegrationTest - Part 14 Real HTTP End-to-End Interview Scheduling Acceptance")
class SchedulingWorkflowRealHttpIntegrationTest {
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
    private InterviewSessionRepository sessionRepository;

    @Autowired
    private InterviewService interviewService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private javax.sql.DataSource dataSource;

    private HttpClient httpClient;

    private static final String STU_1_ID = "STU-E2E-SCH-1";
    private static final String STU_2_ID = "STU-E2E-SCH-2";
    private static final String PASSWORD = "SchedulingWorkflowPass2026!";

    private static final String CMP_ID = "CMP-E2E-SCH";
    private static final String DRV_ID = "DRV-E2E-SCH";
    private static final String INT_ID = "INT-E2E-SCH";
    private static final String ROOM_ID = "RM-E2E-SCH";

    private final List<HireFlowEvent> recordedEvents = new CopyOnWriteArrayList<>();
    private final HireFlowEventListener testListener = recordedEvents::add;

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        cleanDatabase();
        recordedEvents.clear();
        interviewService.addEventListener(testListener);

        // 1. Setup Student 1 (eligible candidate Emma)
        Student student1 = new Student(STU_1_ID, "Emma Watson", "emma@hireflow.test", "CSE", 9.50, 2026, true);
        studentRepository.add(student1);

        // 2. Setup Student 2 (candidate Tom)
        Student student2 = new Student(STU_2_ID, "Tom Holland", "tom@hireflow.test", "CSE", 8.80, 2026, true);
        studentRepository.add(student2);

        // 3. Setup User Accounts
        String hash = passwordHasher.hashPassword(PASSWORD.toCharArray());

        userAccountRepository.save(UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username("officer_e2e_sch")
                .passwordHash(hash)
                .role(UserRole.PLACEMENT_OFFICER)
                .displayName("Officer Evelyn")
                .enabled(true)
                .build());

        userAccountRepository.save(UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username("student_emma")
                .passwordHash(hash)
                .role(UserRole.STUDENT)
                .studentId(STU_1_ID)
                .displayName("Emma Watson")
                .enabled(true)
                .build());

        userAccountRepository.save(UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username("student_tom")
                .passwordHash(hash)
                .role(UserRole.STUDENT)
                .studentId(STU_2_ID)
                .displayName("Tom Holland")
                .enabled(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        interviewService.removeEventListener(testListener);
        cleanDatabase();
    }

    private void cleanDatabase() {
        com.hireflow.api.testsupport.SafeTestDatabase.clean(cleanupDataSource,
            new String[]{"STU-E2E-SCH-1", "STU-E2E-SCH-2", "CMP-E2E-SCH", "DRV-E2E-SCH", "INT-E2E-SCH", "RM-E2E-SCH"},
            new String[]{"officer_e2e_sch", "student_emma", "student_tom"});
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
    @DisplayName("Stage 4E Acceptance: Full Real HTTP Flow (Login -> Drive -> Apply -> Shortlist -> Preview -> Publish -> Retrieve -> Anti-Replay)")
    void testEndToEndInterviewSchedulingAcceptance() throws Exception {
        // Step 1: Placement officer logs in
        ClientSession officerSession = login("officer_e2e_sch", PASSWORD);
        assertNotNull(officerSession.sessionId());

        // Step 2: Student Emma logs in through a separate session
        ClientSession emmaSession = login("student_emma", PASSWORD);
        assertNotNull(emmaSession.sessionId());
        assertNotEquals(officerSession.sessionId(), emmaSession.sessionId());

        // Student Tom logs in through a third session
        ClientSession tomSession = login("student_tom", PASSWORD);
        assertNotNull(tomSession.sessionId());
        assertNotEquals(emmaSession.sessionId(), tomSession.sessionId());

        // Step 3: Officer creates recruitment drive
        // Register company first
        String companyJson = String.format("""
                {
                    "id": "%s",
                    "name": "Apex Quantum Technologies",
                    "industry": "Quantum Computing"
                }
                """, CMP_ID);
        HttpResponse<String> compRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/officer/companies"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + officerSession.sessionId())
                .header(officerSession.csrfHeader(), officerSession.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString(companyJson))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, compRes.statusCode());

        // Register drive
        String driveJson = String.format("""
                {
                    "id": "%s",
                    "companyId": "%s",
                    "jobTitle": "Quantum Algorithm Engineer",
                    "minimumCgpa": 8.0,
                    "eligibleBranches": ["CSE"],
                    "applicationDeadline": "%s"
                }
                """, DRV_ID, CMP_ID, LocalDate.now().plusMonths(1));
        HttpResponse<String> driveRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/officer/drives"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + officerSession.sessionId())
                .header(officerSession.csrfHeader(), officerSession.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString(driveJson))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, driveRes.statusCode());

        // Step 4: Student Emma submits an application
        String appSubmitJson = String.format("{\"driveId\":\"%s\"}", DRV_ID);
        HttpResponse<String> appSubmitRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/student/applications"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + emmaSession.sessionId())
                .header(emmaSession.csrfHeader(), emmaSession.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString(appSubmitJson))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, appSubmitRes.statusCode());
        String emmaAppId = JsonPath.read(appSubmitRes.body(), "$.id");
        assertNotNull(emmaAppId);

        // Step 5: Officer shortlists Emma's application
        HttpResponse<String> shortlistRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/officer/applications/" + emmaAppId + "/shortlist"))
                .header("Cookie", "JSESSIONID=" + officerSession.sessionId())
                .header(officerSession.csrfHeader(), officerSession.csrfToken())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, shortlistRes.statusCode());
        assertEquals("SHORTLISTED", JsonPath.read(shortlistRes.body(), "$.status"));

        // Step 6: Officer configures valid interview resources
        // Add Interviewer
        String interviewerJson = String.format("""
                {
                    "id": "%s",
                    "name": "Dr. Richard Feynman",
                    "companyId": "%s"
                }
                """, INT_ID, CMP_ID);
        HttpResponse<String> intRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/officer/interviewers"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + officerSession.sessionId())
                .header(officerSession.csrfHeader(), officerSession.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString(interviewerJson))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, intRes.statusCode());

        // Add Room
        String roomJson = String.format("""
                {
                    "id": "%s",
                    "name": "Quantum Lab Room 1",
                    "capacity": 8
                }
                """, ROOM_ID);
        HttpResponse<String> roomRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/officer/rooms"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + officerSession.sessionId())
                .header(officerSession.csrfHeader(), officerSession.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString(roomJson))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, roomRes.statusCode());

        // Step 7: Officer requests a scheduling preview
        LocalDate scheduleDate = LocalDate.now().plusDays(2);
        String previewRequestJson = String.format("""
                {
                    "driveId": "%s",
                    "date": "%s",
                    "startTime": "09:00",
                    "endTime": "17:00",
                    "durationMinutes": 45,
                    "bufferMinutes": 15,
                    "interviewerIds": ["%s"],
                    "roomIds": ["%s"]
                }
                """, DRV_ID, scheduleDate, INT_ID, ROOM_ID);
        HttpResponse<String> previewRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/officer/scheduling/preview"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + officerSession.sessionId())
                .header(officerSession.csrfHeader(), officerSession.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString(previewRequestJson))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, previewRes.statusCode());
        String previewId = JsonPath.read(previewRes.body(), "$.previewId");
        assertNotNull(previewId);
        assertTrue(previewId.startsWith("PREV-"));
        assertEquals(1, (int) JsonPath.read(previewRes.body(), "$.totalScheduled"));

        // Step 8: Confirm preview did not change database records
        assertEquals(0, fixtureSessionCount(), "Database must contain ZERO interview sessions after preview.");
        assertEquals("SHORTLISTED", applicationRepository.findById(emmaAppId).get().getStatus().name());
        assertEquals(0, recordedEvents.stream().filter(e -> e.type() == HireFlowEventType.INTERVIEW_SCHEDULED).count(),
                "Zero INTERVIEW_SCHEDULED events must be broadcast during preview.");

        // Step 9: Officer publishes the preview
        String publishRequestJson = String.format("{\"previewId\":\"%s\"}", previewId);
        HttpResponse<String> publishRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/officer/scheduling/publish"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + officerSession.sessionId())
                .header(officerSession.csrfHeader(), officerSession.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString(publishRequestJson))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, publishRes.statusCode());
        assertEquals(1, (int) JsonPath.read(publishRes.body(), "$.publishedCount"));
        assertEquals(0, (int) JsonPath.read(publishRes.body(), "$.conflictsCount"));
        String confirmedSessionId = JsonPath.read(publishRes.body(), "$.publishedSessions[0].sessionId");
        assertNotNull(confirmedSessionId);

        // Step 10 & 11: Confirm interview sessions are persisted and application transitioned to SCHEDULED
        assertEquals(1, fixtureSessionCount());
        assertTrue(sessionRepository.existsById(confirmedSessionId));
        assertEquals("SCHEDULED", applicationRepository.findById(emmaAppId).get().getStatus().name());

        // Step 12: Student Emma fetches confirmed interview timetable
        HttpResponse<String> emmaInterviewsRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/student/interviews"))
                .header("Cookie", "JSESSIONID=" + emmaSession.sessionId())
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, emmaInterviewsRes.statusCode());
        List<?> emmaInterviews = JsonPath.read(emmaInterviewsRes.body(), "$");
        assertEquals(1, emmaInterviews.size());
        assertEquals(confirmedSessionId, JsonPath.read(emmaInterviewsRes.body(), "$[0].sessionId"));
        assertEquals("Emma Watson", JsonPath.read(emmaInterviewsRes.body(), "$[0].studentName"));
        assertEquals("Quantum Lab Room 1", JsonPath.read(emmaInterviewsRes.body(), "$[0].roomName"));
        assertEquals("Dr. Richard Feynman", JsonPath.read(emmaInterviewsRes.body(), "$[0].interviewerName"));

        // Step 13: Officer fetches confirmed timetable
        HttpResponse<String> officerPublishedRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/officer/scheduling/published"))
                .header("Cookie", "JSESSIONID=" + officerSession.sessionId())
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, officerPublishedRes.statusCode());
        int totalElements = JsonPath.read(officerPublishedRes.body(), "$.totalElements");
        assertTrue(totalElements >= 1);
        assertEquals(confirmedSessionId, JsonPath.read(officerPublishedRes.body(), "$.content[0].sessionId"));

        // Step 14 & 15: Repeat publication using same preview token -> Verify duplicate is rejected without side effects (409)
        HttpResponse<String> replayRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/officer/scheduling/publish"))
                .header("Content-Type", "application/json")
                .header("Cookie", "JSESSIONID=" + officerSession.sessionId())
                .header(officerSession.csrfHeader(), officerSession.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString(publishRequestJson))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(409, replayRes.statusCode());
        assertTrue(replayRes.body().contains("already been published"));
        assertEquals(1, fixtureSessionCount(), "Replay must NOT create additional sessions.");

        // Step 16: Verify events were dispatched after commit
        List<HireFlowEvent> schedEvents = recordedEvents.stream()
                .filter(e -> e.type() == HireFlowEventType.INTERVIEW_SCHEDULED)
                .toList();
        assertEquals(1, schedEvents.size(), "Exactly 1 INTERVIEW_SCHEDULED event must be dispatched post-commit.");
        assertEquals(confirmedSessionId, schedEvents.get(0).sourceId());

        // Step 17 & 19: Verify no cross-student data exposure
        // Tom calls getStudentInterviews
        HttpResponse<String> tomInterviewsRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/student/interviews"))
                .header("Cookie", "JSESSIONID=" + tomSession.sessionId())
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, tomInterviewsRes.statusCode());
        List<?> tomInterviews = JsonPath.read(tomInterviewsRes.body(), "$");
        assertEquals(0, tomInterviews.size(), "Student Tom must see 0 interviews.");

        // Tom attempts to inspect Emma's interview by ID -> non-disclosing 404
        HttpResponse<String> tomCrossRes = httpClient.send(HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/v1/student/interviews/" + confirmedSessionId))
                .header("Cookie", "JSESSIONID=" + tomSession.sessionId())
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(404, tomCrossRes.statusCode(), "Cross-student access must return 404 Not Found.");
    }
    /** Count only appointments owned by this class's drives; preserve other runs. */
    private long fixtureSessionCount() {
        return sessionRepository.findAll().stream().filter(session ->
            applicationRepository.findById(session.getApplicationId())
                .map(app -> java.util.Set.of(DRV_ID).contains(app.getDriveId())).orElse(false)).count();
    }
}
