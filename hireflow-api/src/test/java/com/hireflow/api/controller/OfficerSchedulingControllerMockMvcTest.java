package com.hireflow.api.controller;

import com.hireflow.api.dto.scheduling.OfficerSchedulingPreviewRequestDto;
import com.hireflow.api.dto.scheduling.OfficerSchedulingPublishRequestDto;
import com.hireflow.api.security.HireFlowUserPrincipal;
import com.hireflow.api.service.SchedulingPreviewCacheService;
import com.hireflow.model.*;
import com.hireflow.repository.*;
import com.hireflow.web.dto.SchedulingPreviewState;
import com.hireflow.web.security.PasswordHasher;
import com.hireflow.web.security.UserAccount;
import com.hireflow.web.security.UserAccountRepository;
import com.hireflow.web.security.UserPrincipal;
import com.hireflow.web.security.UserRole;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("OfficerSchedulingControllerMockMvcTest - Secure REST Scheduling Preview, Publication, and Retrieval")
class OfficerSchedulingControllerMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

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
    private SchedulingPreviewCacheService previewCacheService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private javax.sql.DataSource dataSource;

    private Authentication officerAuth;
    private Authentication otherOfficerAuth;
    private Authentication studentAuth;

    private static final String CMP_ID = "CMP-MVC-SCH";
    private static final String DRV_ID = "DRV-MVC-SCH";
    private static final String STU_1_ID = "STU-MVC-SCH1";
    private static final String STU_2_ID = "STU-MVC-SCH2";
    private static final String APP_1_ID = "APP-MVC-SCH1";
    private static final String APP_2_ID = "APP-MVC-SCH2";
    private static final String INT_1_ID = "INT-MVC-SCH1";
    private static final String ROOM_1_ID = "RM-MVC-SCH1";

    @BeforeEach
    void setUp() {
        cleanDatabase();

        // 1. Company
        companyRepository.add(new Company(CMP_ID, "Vanguard Systems", "FinTech"));

        // 2. Drive
        driveRepository.add(new RecruitmentDrive(
                DRV_ID, CMP_ID, "Software Engineer - Backend", 7.00, Set.of("CSE", "IT"),
                LocalDate.now().plusMonths(2), false
        ));

        // 3. Students
        studentRepository.add(new Student(STU_1_ID, "Diana Prince", "diana@hireflow.test", "CSE", 8.80, 2026, true));
        studentRepository.add(new Student(STU_2_ID, "Clark Kent", "clark@hireflow.test", "CSE", 8.50, 2026, true));

        // 4. Shortlisted Applications
        Application app1 = new Application(APP_1_ID, STU_1_ID, DRV_ID, ApplicationStatus.SHORTLISTED, LocalDateTime.now().minusDays(2), "Shortlisted by exam");
        Application app2 = new Application(APP_2_ID, STU_2_ID, DRV_ID, ApplicationStatus.SHORTLISTED, LocalDateTime.now().minusDays(1), "Shortlisted by exam");
        applicationRepository.add(app1);
        applicationRepository.add(app2);

        // 5. Interviewer & Room
        interviewerRepository.add(new Interviewer(INT_1_ID, "Bruce Wayne", CMP_ID));
        roomRepository.add(new Room(ROOM_1_ID, "Executive Boardroom", 12));

        // 6. Security Accounts
        String hash = passwordHasher.hashPassword("OfficerPass2026!".toCharArray());
        userAccountRepository.save(UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username("officer_claire")
                .passwordHash(hash)
                .role(UserRole.PLACEMENT_OFFICER)
                .displayName("Officer Claire")
                .enabled(true)
                .build());

        userAccountRepository.save(UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username("officer_other")
                .passwordHash(hash)
                .role(UserRole.PLACEMENT_OFFICER)
                .displayName("Officer Other")
                .enabled(true)
                .build());

        userAccountRepository.save(UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username("student_diana")
                .passwordHash(hash)
                .role(UserRole.STUDENT)
                .studentId(STU_1_ID)
                .displayName("Diana Prince")
                .enabled(true)
                .build());

        HireFlowUserPrincipal officerPrincipal = new HireFlowUserPrincipal(
                new UserPrincipal("officer_claire", "Officer Claire", UserRole.PLACEMENT_OFFICER, null)
        );
        officerAuth = new UsernamePasswordAuthenticationToken(officerPrincipal, null, officerPrincipal.getAuthorities());

        HireFlowUserPrincipal otherOfficerPrincipal = new HireFlowUserPrincipal(
                new UserPrincipal("officer_other", "Officer Other", UserRole.PLACEMENT_OFFICER, null)
        );
        otherOfficerAuth = new UsernamePasswordAuthenticationToken(otherOfficerPrincipal, null, otherOfficerPrincipal.getAuthorities());

        HireFlowUserPrincipal studentPrincipal = new HireFlowUserPrincipal(
                new UserPrincipal("student_diana", "Diana Prince", UserRole.STUDENT, STU_1_ID)
        );
        studentAuth = new UsernamePasswordAuthenticationToken(studentPrincipal, null, studentPrincipal.getAuthorities());
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
        previewCacheService.clear();
    }

    private void cleanDatabase() {
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM interview_sessions WHERE application_id IN (SELECT id FROM applications WHERE id LIKE 'APP-MVC-%' OR drive_id = '" + DRV_ID + "')");
            stmt.executeUpdate("DELETE FROM interview_sessions WHERE id LIKE 'SES-%'");
            stmt.executeUpdate("DELETE FROM applications WHERE id LIKE 'APP-MVC-%' OR drive_id = '" + DRV_ID + "'");
            stmt.executeUpdate("DELETE FROM interviewers WHERE id LIKE 'INT-MVC-%'");
            stmt.executeUpdate("DELETE FROM rooms WHERE id LIKE 'RM-MVC-%'");
            stmt.executeUpdate("DELETE FROM recruitment_drives WHERE id LIKE 'DRV-MVC-%'");
            stmt.executeUpdate("DELETE FROM companies WHERE id LIKE 'CMP-MVC-%'");
            stmt.executeUpdate("DELETE FROM user_accounts WHERE username IN ('officer_claire', 'officer_other', 'student_diana')");
            stmt.executeUpdate("DELETE FROM students WHERE id LIKE 'STU-MVC-%'");
        } catch (Exception e) {
            System.err.println("cleanDatabase error: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("POST /preview - Success: Generates advisory preview with zero database mutation")
    void testGeneratePreview_success() throws Exception {
        LocalDate interviewDate = LocalDate.now().plusDays(3);
        String body = String.format("""
                {
                    "driveId": "%s",
                    "date": "%s",
                    "startTime": "09:00",
                    "endTime": "17:00",
                    "durationMinutes": 45,
                    "bufferMinutes": 15
                }
                """, DRV_ID, interviewDate);

        mockMvc.perform(post("/api/v1/officer/scheduling/preview")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.previewId", startsWith("PREV-")))
                .andExpect(jsonPath("$.driveId", is(DRV_ID)))
                .andExpect(jsonPath("$.jobTitle", is("Software Engineer - Backend")))
                .andExpect(jsonPath("$.companyName", is("Vanguard Systems")))
                .andExpect(jsonPath("$.totalRequested", is(2)))
                .andExpect(jsonPath("$.totalScheduled", is(2)))
                .andExpect(jsonPath("$.totalUnscheduled", is(0)))
                .andExpect(jsonPath("$.proposedSessions", hasSize(2)))
                .andExpect(jsonPath("$.expiresAt", notNullValue()));

        // Crucial invariant: zero database mutation during preview!
        assertEquals(0, sessionRepository.count(), "Preview must NOT insert any interview sessions.");
        assertEquals(ApplicationStatus.SHORTLISTED, applicationRepository.findById(APP_1_ID).get().getStatus());
        assertEquals(ApplicationStatus.SHORTLISTED, applicationRepository.findById(APP_2_ID).get().getStatus());
    }

    @Test
    @DisplayName("POST /preview - Security: Missing CSRF token is rejected with 403 Forbidden")
    void testGeneratePreview_missingCsrf() throws Exception {
        String body = String.format("""
                {
                    "driveId": "%s",
                    "date": "%s",
                    "startTime": "09:00",
                    "endTime": "17:00"
                }
                """, DRV_ID, LocalDate.now().plusDays(3));

        mockMvc.perform(post("/api/v1/officer/scheduling/preview")
                        .with(authentication(officerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /preview - Security: Student role is forbidden from generating scheduling preview")
    void testGeneratePreview_forbiddenRole_student() throws Exception {
        String body = String.format("""
                {
                    "driveId": "%s",
                    "date": "%s",
                    "startTime": "09:00",
                    "endTime": "17:00"
                }
                """, DRV_ID, LocalDate.now().plusDays(3));

        mockMvc.perform(post("/api/v1/officer/scheduling/preview")
                        .with(authentication(studentAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /preview - Security: Unauthenticated request is rejected with 401 Unauthorized")
    void testGeneratePreview_unauthenticated() throws Exception {
        String body = String.format("""
                {
                    "driveId": "%s",
                    "date": "%s",
                    "startTime": "09:00",
                    "endTime": "17:00"
                }
                """, DRV_ID, LocalDate.now().plusDays(3));

        mockMvc.perform(post("/api/v1/officer/scheduling/preview")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /preview - Validation: Non-existent drive returns 404 Not Found")
    void testGeneratePreview_invalidDrive() throws Exception {
        String body = String.format("""
                {
                    "driveId": "DRV-NONEXISTENT",
                    "date": "%s",
                    "startTime": "09:00",
                    "endTime": "17:00"
                }
                """, LocalDate.now().plusDays(3));

        mockMvc.perform(post("/api/v1/officer/scheduling/preview")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /preview - Validation: Invalid time window (start >= end) returns 400 Bad Request")
    void testGeneratePreview_invalidTimes() throws Exception {
        String body = String.format("""
                {
                    "driveId": "%s",
                    "date": "%s",
                    "startTime": "17:00",
                    "endTime": "09:00"
                }
                """, DRV_ID, LocalDate.now().plusDays(3));

        mockMvc.perform(post("/api/v1/officer/scheduling/preview")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /publish - Success: Persists confirmed sessions and updates application status")
    void testPublishSchedule_success() throws Exception {
        // 1. Generate preview
        String previewBody = String.format("""
                {
                    "driveId": "%s",
                    "date": "%s",
                    "startTime": "09:00",
                    "endTime": "17:00",
                    "durationMinutes": 45,
                    "bufferMinutes": 15
                }
                """, DRV_ID, LocalDate.now().plusDays(3));

        MvcResult previewResult = mockMvc.perform(post("/api/v1/officer/scheduling/preview")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody))
                .andExpect(status().isOk())
                .andReturn();

        String previewId = JsonPath.read(previewResult.getResponse().getContentAsString(), "$.previewId");
        assertNotNull(previewId);

        // 2. Publish schedule
        String publishBody = String.format("{\"previewId\": \"%s\"}", previewId);
        mockMvc.perform(post("/api/v1/officer/scheduling/publish")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.previewId", is(previewId)))
                .andExpect(jsonPath("$.publishedCount", is(2)))
                .andExpect(jsonPath("$.conflictsCount", is(0)))
                .andExpect(jsonPath("$.errorsCount", is(0)))
                .andExpect(jsonPath("$.publishedSessions", hasSize(2)));

        // 3. Verify PostgreSQL persistence
        assertEquals(2, sessionRepository.count());
        assertEquals(ApplicationStatus.SCHEDULED, applicationRepository.findById(APP_1_ID).get().getStatus());
        assertEquals(ApplicationStatus.SCHEDULED, applicationRepository.findById(APP_2_ID).get().getStatus());
    }

    @Test
    @DisplayName("POST /publish - Security: Token replay is rejected with 409 Conflict")
    void testPublishSchedule_tokenReplayRejection() throws Exception {
        // 1. Generate preview
        String previewBody = String.format("""
                {
                    "driveId": "%s",
                    "date": "%s",
                    "startTime": "09:00",
                    "endTime": "17:00",
                    "durationMinutes": 45,
                    "bufferMinutes": 15
                }
                """, DRV_ID, LocalDate.now().plusDays(3));

        MvcResult previewResult = mockMvc.perform(post("/api/v1/officer/scheduling/preview")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody))
                .andExpect(status().isOk())
                .andReturn();

        String previewId = JsonPath.read(previewResult.getResponse().getContentAsString(), "$.previewId");

        // 2. First publish -> 200 OK
        String publishBody = String.format("{\"previewId\": \"%s\"}", previewId);
        mockMvc.perform(post("/api/v1/officer/scheduling/publish")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody))
                .andExpect(status().isOk());

        // 3. Second publish with same token -> 409 Conflict (Replay blocked)
        mockMvc.perform(post("/api/v1/officer/scheduling/publish")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already been published")));
    }

    @Test
    @DisplayName("POST /publish - Security: Cross-officer token reuse is rejected with 403 Forbidden")
    void testPublishSchedule_crossOfficerAttempt() throws Exception {
        // Officer Claire generates preview
        String previewBody = String.format("""
                {
                    "driveId": "%s",
                    "date": "%s",
                    "startTime": "09:00",
                    "endTime": "17:00"
                }
                """, DRV_ID, LocalDate.now().plusDays(3));

        MvcResult previewResult = mockMvc.perform(post("/api/v1/officer/scheduling/preview")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody))
                .andExpect(status().isOk())
                .andReturn();

        String previewId = JsonPath.read(previewResult.getResponse().getContentAsString(), "$.previewId");

        // Officer Other attempts to publish Claire's preview
        String publishBody = String.format("{\"previewId\": \"%s\"}", previewId);
        mockMvc.perform(post("/api/v1/officer/scheduling/publish")
                        .with(authentication(otherOfficerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Preview belongs to another officer")));
    }

    @Test
    @DisplayName("POST /publish - Error Handling: Unknown preview token returns 404 Not Found")
    void testPublishSchedule_unknownToken() throws Exception {
        String publishBody = "{\"previewId\": \"PREV-UNKNOWN-TOKEN-12345\"}";
        mockMvc.perform(post("/api/v1/officer/scheduling/publish")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /publish - Error Handling: Expired preview returns 410 Gone")
    void testPublishSchedule_expiredToken() throws Exception {
        // Manually insert an expired preview into cache
        String expiredPreviewId = "PREV-EXPIRED-TEST-TOKEN";
        Instant past = Instant.now().minusSeconds(100);
        SchedulingPreviewState expiredState = new SchedulingPreviewState(
                expiredPreviewId,
                "officer_claire",
                past.minusSeconds(900),
                past, // expired!
                false,
                DRV_ID,
                "Job",
                "Company",
                LocalDate.now(),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                45,
                15,
                1,
                1,
                0,
                List.of(new SchedulingPreviewState.ProposedSessionDto(
                        "SES-1", APP_1_ID, STU_1_ID, "Diana", "CSE", INT_1_ID, "Bruce", ROOM_1_ID, "Room",
                        LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusMinutes(45)
                )),
                List.of()
        );
        previewCacheService.put(expiredState);

        String publishBody = String.format("{\"previewId\": \"%s\"}", expiredPreviewId);
        mockMvc.perform(post("/api/v1/officer/scheduling/publish")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message", containsString("expired")));
    }

    @Test
    @DisplayName("GET /published - Success: Retrieves confirmed interview schedule from PostgreSQL")
    void testGetPublishedSchedule_success() throws Exception {
        // Add confirmed session
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);
        LocalDateTime end = start.plusMinutes(45);
        InterviewSession session = new InterviewSession("SES-PUB-01", APP_1_ID, INT_1_ID, ROOM_1_ID, start, end);
        sessionRepository.add(session);

        mockMvc.perform(get("/api/v1/officer/scheduling/published")
                        .with(authentication(officerAuth)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].sessionId", is("SES-PUB-01")))
                .andExpect(jsonPath("$.content[0].applicationId", is(APP_1_ID)))
                .andExpect(jsonPath("$.content[0].studentName", is("Diana Prince")))
                .andExpect(jsonPath("$.content[0].interviewerName", is("Bruce Wayne")))
                .andExpect(jsonPath("$.content[0].roomName", is("Executive Boardroom")));
    }

    @Test
    @DisplayName("GET /published/{id} - Success & 404: Inspect confirmed session by ID")
    void testGetPublishedScheduleById() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);
        LocalDateTime end = start.plusMinutes(45);
        InterviewSession session = new InterviewSession("SES-PUB-02", APP_2_ID, INT_1_ID, ROOM_1_ID, start, end);
        sessionRepository.add(session);

        // Found
        mockMvc.perform(get("/api/v1/officer/scheduling/published/SES-PUB-02")
                        .with(authentication(officerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId", is("SES-PUB-02")))
                .andExpect(jsonPath("$.studentName", is("Clark Kent")));

        // Not Found
        mockMvc.perform(get("/api/v1/officer/scheduling/published/SES-UNKNOWN")
                        .with(authentication(officerAuth)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /publish - Partial Success: One session commits and one conflicts, returning 207 Multi-Status")
    void testPublishSchedule_partialSuccess_returns207MultiStatus() throws Exception {
        // 1. Generate preview with 2 applications (scheduled at 09:00 and 10:00)
        String previewBody = String.format("""
                {
                    "driveId": "%s",
                    "date": "%s",
                    "startTime": "09:00",
                    "endTime": "17:00",
                    "durationMinutes": 45,
                    "bufferMinutes": 15
                }
                """, DRV_ID, LocalDate.now().plusDays(3));

        MvcResult previewResult = mockMvc.perform(post("/api/v1/officer/scheduling/preview")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody))
                .andExpect(status().isOk())
                .andReturn();

        String previewId = JsonPath.read(previewResult.getResponse().getContentAsString(), "$.previewId");
        assertNotNull(previewId);

        // 2. Pre-empt the second slot (10:00 - 10:45) by inserting a conflicting session for an external student/app
        LocalDateTime conflictStart = LocalDate.now().plusDays(3).atTime(10, 0);
        LocalDateTime conflictEnd = conflictStart.plusMinutes(45);
        Student extStudent = new Student("STU-MVC-EXT", "External Candidate", "ext@hireflow.test", "CSE", 9.0, 2026, true);
        studentRepository.add(extStudent);
        Application extApp = new Application("APP-MVC-EXT", "STU-MVC-EXT", DRV_ID, ApplicationStatus.SCHEDULED, LocalDateTime.now().minusDays(1), "Ext");
        applicationRepository.add(extApp);
        InterviewSession extSession = new InterviewSession("SES-MVC-EXT", "APP-MVC-EXT", INT_1_ID, ROOM_1_ID, conflictStart, conflictEnd);
        sessionRepository.add(extSession);

        // 3. Publish schedule -> Session 1 (09:00) will succeed, Session 2 (10:00) will conflict with extSession!
        String publishBody = String.format("{\"previewId\": \"%s\"}", previewId);
        mockMvc.perform(post("/api/v1/officer/scheduling/publish")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody))
                .andExpect(status().is(207))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.outcome", is("PARTIAL_SUCCESS")))
                .andExpect(jsonPath("$.publishedCount", is(1)))
                .andExpect(jsonPath("$.conflictsCount", is(1)))
                .andExpect(jsonPath("$.totalProposals", is(2)))
                .andExpect(jsonPath("$.unattemptedCount", is(0)))
                .andExpect(jsonPath("$.attemptId", notNullValue()))
                .andExpect(jsonPath("$.previewReference", startsWith("PREV-")))
                .andExpect(jsonPath("$.recoveryInstructions", containsString("Partial publication:")))
                .andExpect(jsonPath("$.publishedSessions", hasSize(1)));

        // 4. Verify DB state: 1 new session added (total 2 with the external one)
        assertEquals(2, sessionRepository.count());
        assertEquals(ApplicationStatus.SCHEDULED, applicationRepository.findById(APP_1_ID).get().getStatus());
        assertEquals(ApplicationStatus.SHORTLISTED, applicationRepository.findById(APP_2_ID).get().getStatus());
    }

    @Test
    @DisplayName("POST /publish - Total Conflict: Zero sessions commit, returning 409 Conflict")
    void testPublishSchedule_totalConflict_returns409Conflict() throws Exception {
        // 1. Transition APP_1 to REJECTED so it has 1 applicant who will fail status verification
        applicationRepository.updateStatusConditional(APP_2_ID, ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED, "Not shortlisted");

        String previewBody = String.format("""
                {
                    "driveId": "%s",
                    "date": "%s",
                    "startTime": "09:00",
                    "endTime": "17:00",
                    "durationMinutes": 45,
                    "bufferMinutes": 15
                }
                """, DRV_ID, LocalDate.now().plusDays(3));

        // Generate preview for the single shortlisted candidate (APP_1)
        MvcResult previewResult = mockMvc.perform(post("/api/v1/officer/scheduling/preview")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(previewBody))
                .andExpect(status().isOk())
                .andReturn();

        String previewId = JsonPath.read(previewResult.getResponse().getContentAsString(), "$.previewId");

        // Now change APP_1 status to REJECTED before publish, creating total conflict
        applicationRepository.updateStatusConditional(APP_1_ID, ApplicationStatus.SHORTLISTED, ApplicationStatus.REJECTED, "Stale status");

        // 2. Publish schedule -> 0 succeed, 1 conflict -> 409 Conflict with NONE_CONFIRMED
        String publishBody = String.format("{\"previewId\": \"%s\"}", previewId);
        mockMvc.perform(post("/api/v1/officer/scheduling/publish")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.outcome", is("NONE_CONFIRMED")))
                .andExpect(jsonPath("$.publishedCount", is(0)))
                .andExpect(jsonPath("$.conflictsCount", is(1)))
                .andExpect(jsonPath("$.recoveryInstructions", containsString("Zero appointments confirmed")));

        assertEquals(0, sessionRepository.count());
    }

    @Test
    @DisplayName("SchedulingPreviewCacheService - FIFO Eviction: Oldest entry by creation time is evicted when limit is reached")
    void testPreviewCache_fifoEvictionPolicy() {
        previewCacheService.clear();

        // Put 1000 entries with future expiration so proactive expiry doesn't drop them
        for (int i = 0; i < 1000; i++) {
            String id = String.format("PREV-TEST-FIFO-%04d", i);
            Instant created = Instant.now().minusSeconds(2000 - i);
            Instant expires = Instant.now().plusSeconds(900 + i); // in future
            SchedulingPreviewState s = new SchedulingPreviewState(
                    id, "officer_claire", created, expires, false,
                    DRV_ID, "Job", "Company", LocalDate.now(), LocalTime.of(9, 0), LocalTime.of(17, 0),
                    45, 15, 0, 0, 0, List.of(), List.of()
            );
            previewCacheService.put(s);
        }

        assertEquals(1000, previewCacheService.size());
        assertTrue(previewCacheService.get("PREV-TEST-FIFO-0000").isPresent(), "Oldest entry should still be present.");

        // Add 1001st entry -> should evict PREV-TEST-FIFO-0000 (the oldest by createdAt)
        String newId = "PREV-TEST-FIFO-1000";
        Instant newCreated = Instant.now();
        Instant newExpires = Instant.now().plusSeconds(1800);
        SchedulingPreviewState newest = new SchedulingPreviewState(
                newId, "officer_claire", newCreated, newExpires, false,
                DRV_ID, "Job", "Company", LocalDate.now(), LocalTime.of(9, 0), LocalTime.of(17, 0),
                45, 15, 0, 0, 0, List.of(), List.of()
        );
        previewCacheService.put(newest);

        assertEquals(1000, previewCacheService.size());
        assertFalse(previewCacheService.get("PREV-TEST-FIFO-0000").isPresent(), "Oldest entry PREV-TEST-FIFO-0000 must be evicted.");
        assertTrue(previewCacheService.get(newId).isPresent(), "Newest entry PREV-TEST-FIFO-1000 must be present.");
    }

    @Test
    @DisplayName("SchedulingPreviewCacheService - Token Masking: Redacts middle characters for logging and DTOs")
    void testPreviewCache_tokenMasking() {
        assertEquals("[REDACTED_PREVIEW_TOKEN]", SchedulingPreviewCacheService.maskToken(null));
        assertEquals("[REDACTED_PREVIEW_TOKEN]", SchedulingPreviewCacheService.maskToken(""));
        assertEquals("PREV-ABC****", SchedulingPreviewCacheService.maskToken("PREV-ABCDEF12"));
        assertEquals("PREV-123****", SchedulingPreviewCacheService.maskToken("PREV-1234567890"));
    }
}
