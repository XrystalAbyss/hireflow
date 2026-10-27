package com.hireflow.web.controller;

import com.hireflow.model.*;
import com.hireflow.repository.*;
import com.hireflow.scheduler.GreedyScheduler;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.EligibilityService;
import com.hireflow.service.InterviewService;
import com.hireflow.web.dto.ConfirmedInterviewViewItem;
import com.hireflow.web.dto.PublicationSummaryState;
import com.hireflow.web.dto.SchedulingPreviewState;
import com.hireflow.web.filter.PortalAuthorizationFilter;
import com.hireflow.web.security.CsrfTokenManager;
import com.hireflow.web.security.UserPrincipal;
import com.hireflow.web.security.UserRole;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("OfficerPublicationSecurityTest - Web Schedule Publication Authorization, Expiration, and Conflict Safety")
class OfficerPublicationSecurityTest {

    private DriveRepository driveRepo;
    private ApplicationRepository appRepo;
    private StudentRepository studentRepo;
    private InterviewerRepository interviewerRepo;
    private RoomRepository roomRepo;
    private InterviewSessionRepository sessionRepo;
    private CompanyRepository companyRepo;
    private InterviewService interviewService;

    private OfficerSchedulingController controller;

    private HttpServletRequest req;
    private HttpServletResponse resp;
    private HttpSession session;
    private RequestDispatcher dispatcher;

    private UserPrincipal officerAlice;
    private UserPrincipal officerBob;
    private UserPrincipal studentCharlie;

    private Map<String, Object> sessionAttributes;

    @BeforeEach
    void setUp() {
        driveRepo = new InMemoryDriveRepository();
        appRepo = new InMemoryApplicationRepository();
        studentRepo = new InMemoryStudentRepository();
        interviewerRepo = new InMemoryInterviewerRepository();
        roomRepo = new InMemoryRoomRepository();
        sessionRepo = new InMemoryInterviewSessionRepository(appRepo);
        companyRepo = new InMemoryCompanyRepository();

        ApplicationService appSvc = new ApplicationService(appRepo, studentRepo, driveRepo, new EligibilityService(), sessionRepo);
        interviewService = new InterviewService(sessionRepo, appRepo, appSvc);

        controller = new OfficerSchedulingController(
                driveRepo, appRepo, studentRepo, interviewerRepo, roomRepo, sessionRepo, companyRepo,
                new GreedyScheduler(), interviewService
        );

        req = mock(HttpServletRequest.class);
        resp = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        dispatcher = mock(RequestDispatcher.class);

        sessionAttributes = new HashMap<>();
        when(session.getAttribute(anyString())).thenAnswer(inv -> sessionAttributes.get(inv.getArgument(0)));
        doAnswer(inv -> {
            sessionAttributes.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(session).setAttribute(anyString(), any());
        doAnswer(inv -> sessionAttributes.remove(inv.getArgument(0))).when(session).removeAttribute(anyString());

        when(req.getSession(anyBoolean())).thenReturn(session);
        when(req.getSession()).thenReturn(session);
        when(req.getContextPath()).thenReturn("/hireflow");
        when(req.getRequestDispatcher(anyString())).thenReturn(dispatcher);

        officerAlice = new UserPrincipal("officer.alice", UserRole.PLACEMENT_OFFICER, "Officer Alice", null);
        officerBob = new UserPrincipal("officer.bob", UserRole.PLACEMENT_OFFICER, "Officer Bob", null);
        studentCharlie = new UserPrincipal("student.charlie", UserRole.STUDENT, "Student Charlie", "STU-01");

        // Seed domain entities
        companyRepo.add(new Company("CMP-01", "Global Systems", "IT"));
        driveRepo.add(new RecruitmentDrive("DRV-01", "CMP-01", "Senior Developer", 8.0, Set.of("CSE"), LocalDate.now().plusDays(7)));

        studentRepo.add(new Student("STU-01", "Alice Student", "alice@test.edu", "CSE", 8.9, 2026, true));
        studentRepo.add(new Student("STU-02", "Bob Student", "bob@test.edu", "CSE", 8.7, 2026, true));

        interviewerRepo.add(new Interviewer("INT-01", "Dr. Alan", "CMP-01"));
        roomRepo.add(new Room("RM-01", "Room 101", 1));

        appRepo.add(new Application("APP-01", "STU-01", "DRV-01", ApplicationStatus.SHORTLISTED));
        appRepo.add(new Application("APP-02", "STU-02", "DRV-01", ApplicationStatus.SHORTLISTED));

        sessionAttributes.put(PortalAuthorizationFilter.SESSION_USER_ATTR, officerAlice);
        sessionAttributes.put(CsrfTokenManager.CSRF_SESSION_ATTR, "csrf-test-token");
        when(req.getParameter("_csrf")).thenReturn("csrf-test-token");
    }

    private SchedulingPreviewState createTestPreview(String previewId, String officerUsername, Instant createdAt, Instant expiresAt, boolean consumed) {
        LocalDateTime start = LocalDateTime.of(2026, 10, 25, 9, 0);
        LocalDateTime end = start.plusMinutes(45);
        List<SchedulingPreviewState.ProposedSessionDto> proposed = List.of(
                new SchedulingPreviewState.ProposedSessionDto(
                        "SESS-01", "APP-01", "STU-01", "Alice Student", "CSE",
                        "INT-01", "Dr. Alan", "RM-01", "Room 101", start, end
                )
        );
        return new SchedulingPreviewState(
                previewId, officerUsername, createdAt, expiresAt, consumed,
                "DRV-01", "Senior Developer", "Global Systems",
                start.toLocalDate(), start.toLocalTime(), LocalTime.of(17, 0),
                45, 15, 1, 1, 0, proposed, List.of()
        );
    }

    @Test
    @DisplayName("publish: student is denied publication access with 403 Forbidden")
    void testStudentDeniedPublicationAccess() throws ServletException, IOException {
        sessionAttributes.put(PortalAuthorizationFilter.SESSION_USER_ATTR, studentCharlie);
        when(req.getPathInfo()).thenReturn("/publish");

        controller.doPost(req, resp);

        verify(resp).sendError(eq(HttpServletResponse.SC_FORBIDDEN), contains("Officer account required"));
    }

    @Test
    @DisplayName("publish: missing or invalid CSRF token is rejected with 403 Forbidden")
    void testMissingCsrfTokenRejected() throws ServletException, IOException {
        when(req.getParameter("_csrf")).thenReturn(null);
        when(req.getPathInfo()).thenReturn("/publish");

        controller.doPost(req, resp);

        verify(resp).sendError(eq(HttpServletResponse.SC_FORBIDDEN), contains("CSRF"));
    }

    @Test
    @DisplayName("publish: missing preview reference is rejected with flash error")
    void testMissingPreviewReferenceRejected() throws ServletException, IOException {
        when(req.getPathInfo()).thenReturn("/publish");
        when(req.getParameter("previewId")).thenReturn("");

        controller.doPost(req, resp);

        assertEquals("Missing preview identifier. Please generate a schedule preview before publishing.",
                sessionAttributes.get("flash_error"));
        verify(resp).sendRedirect(contains("/officer/scheduling"));
    }

    @Test
    @DisplayName("publish: expired preview reference (>15 minutes) is rejected")
    void testExpiredPreviewRejected() throws ServletException, IOException {
        Instant past = Instant.now().minus(Duration.ofMinutes(20));
        SchedulingPreviewState expired = createTestPreview("PREV-EXPIRED", "officer.alice", past, past.plus(Duration.ofMinutes(15)), false);

        Map<String, SchedulingPreviewState> map = new HashMap<>();
        map.put("PREV-EXPIRED", expired);
        sessionAttributes.put(OfficerSchedulingController.PREVIEWS_MAP_SESSION_ATTR, map);

        when(req.getPathInfo()).thenReturn("/publish");
        when(req.getParameter("previewId")).thenReturn("PREV-EXPIRED");

        controller.doPost(req, resp);

        assertTrue(((String) sessionAttributes.get("flash_error")).contains("expired"));
        verify(resp).sendRedirect(contains("/officer/scheduling?driveId=DRV-01"));
        assertEquals(0, sessionRepo.count(), "Expired preview must not insert any sessions");
    }

    @Test
    @DisplayName("publish: cross-officer preview access is blocked with 403 Forbidden")
    void testCrossOfficerPreviewBlocked() throws ServletException, IOException {
        // Preview generated by Alice
        SchedulingPreviewState alicePreview = createTestPreview("PREV-ALICE", "officer.alice", Instant.now(), Instant.now().plus(Duration.ofMinutes(15)), false);

        Map<String, SchedulingPreviewState> map = new HashMap<>();
        map.put("PREV-ALICE", alicePreview);
        sessionAttributes.put(OfficerSchedulingController.PREVIEWS_MAP_SESSION_ATTR, map);

        // Bob tries to publish Alice's preview
        sessionAttributes.put(PortalAuthorizationFilter.SESSION_USER_ATTR, officerBob);
        when(req.getPathInfo()).thenReturn("/publish");
        when(req.getParameter("previewId")).thenReturn("PREV-ALICE");

        controller.doPost(req, resp);

        verify(resp).sendError(eq(HttpServletResponse.SC_FORBIDDEN), contains("another officer account"));
        assertEquals(0, sessionRepo.count());
    }

    @Test
    @DisplayName("publish: multi-tab preview isolation allows separate previews without collision")
    void testMultiTabPreviewIsolation() throws ServletException, IOException {
        Instant now = Instant.now();
        SchedulingPreviewState previewTab1 = createTestPreview("PREV-TAB-1", "officer.alice", now, now.plus(Duration.ofMinutes(15)), false);
        SchedulingPreviewState previewTab2 = createTestPreview("PREV-TAB-2", "officer.alice", now, now.plus(Duration.ofMinutes(15)), false);

        Map<String, SchedulingPreviewState> previewsMap = new HashMap<>();
        previewsMap.put("PREV-TAB-1", previewTab1);
        previewsMap.put("PREV-TAB-2", previewTab2);
        sessionAttributes.put(OfficerSchedulingController.PREVIEWS_MAP_SESSION_ATTR, previewsMap);

        // Publish TAB-2 specifically
        when(req.getPathInfo()).thenReturn("/publish");
        when(req.getParameter("previewId")).thenReturn("PREV-TAB-2");

        controller.doPost(req, resp);

        PublicationSummaryState summary = (PublicationSummaryState) sessionAttributes.get(OfficerSchedulingController.PUBLICATION_RESULT_SESSION_ATTR);
        assertNotNull(summary);
        assertEquals("PREV-TAB-2", summary.previewId());
        assertEquals(1, summary.publishedCount());

        // TAB-1 remains unconsumed and ready in session map
        SchedulingPreviewState tab1Remaining = previewsMap.get("PREV-TAB-1");
        assertNotNull(tab1Remaining);
        assertFalse(tab1Remaining.isConsumed());

        // TAB-2 is consumed
        SchedulingPreviewState tab2Consumed = previewsMap.get("PREV-TAB-2");
        assertNotNull(tab2Consumed);
        assertTrue(tab2Consumed.isConsumed());
    }

    @Test
    @DisplayName("publish: replaying already consumed preview is blocked")
    void testReplayPublicationBlocked() throws ServletException, IOException {
        Instant now = Instant.now();
        SchedulingPreviewState consumed = createTestPreview("PREV-CONSUMED", "officer.alice", now, now.plus(Duration.ofMinutes(15)), true);

        Map<String, SchedulingPreviewState> map = new HashMap<>();
        map.put("PREV-CONSUMED", consumed);
        sessionAttributes.put(OfficerSchedulingController.PREVIEWS_MAP_SESSION_ATTR, map);

        when(req.getPathInfo()).thenReturn("/publish");
        when(req.getParameter("previewId")).thenReturn("PREV-CONSUMED");

        controller.doPost(req, resp);

        assertTrue(((String) sessionAttributes.get("flash_error")).contains("already been published. Replay prevented"));
        verify(resp).sendRedirect(contains("/officer/scheduling/published"));
    }

    @Test
    @DisplayName("publish: successful publication persists sessions, updates status to SCHEDULED, and redirects")
    void testSuccessfulPublication() throws ServletException, IOException {
        Instant now = Instant.now();
        SchedulingPreviewState preview = createTestPreview("PREV-SUCCESS", "officer.alice", now, now.plus(Duration.ofMinutes(15)), false);

        Map<String, SchedulingPreviewState> map = new HashMap<>();
        map.put("PREV-SUCCESS", preview);
        sessionAttributes.put(OfficerSchedulingController.PREVIEWS_MAP_SESSION_ATTR, map);

        when(req.getPathInfo()).thenReturn("/publish");
        when(req.getParameter("previewId")).thenReturn("PREV-SUCCESS");

        controller.doPost(req, resp);

        // Verification 1: Session persisted in repository
        assertEquals(1, sessionRepo.count());
        InterviewSession saved = sessionRepo.findById("SESS-01").orElseThrow();
        assertEquals("APP-01", saved.getApplicationId());
        assertEquals("INT-01", saved.getInterviewerId());
        assertEquals("RM-01", saved.getRoomId());

        // Verification 2: Application updated from SHORTLISTED to SCHEDULED
        Application updatedApp = appRepo.findById("APP-01").orElseThrow();
        assertEquals(ApplicationStatus.SCHEDULED, updatedApp.getStatus());

        // Verification 3: Flash publication summary populated
        PublicationSummaryState summary = (PublicationSummaryState) sessionAttributes.get(OfficerSchedulingController.PUBLICATION_RESULT_SESSION_ATTR);
        assertNotNull(summary);
        assertEquals(1, summary.publishedCount());
        assertEquals(0, summary.conflictsCount());
        assertTrue(summary.isCompletelySuccessful());

        // Verification 4: Post/Redirect/Get redirect
        verify(resp).sendRedirect(contains("/officer/scheduling/published?driveId=DRV-01"));
    }

    @Test
    @DisplayName("publish: conflict revalidation leaves application status as SHORTLISTED and reports diagnostics")
    void testConflictRevalidationPreservesApplicationStatus() throws ServletException, IOException {
        // Pre-occupy Room RM-01 with an overlapping confirmed session
        LocalDateTime start = LocalDateTime.of(2026, 10, 25, 9, 0);
        sessionRepo.add(new InterviewSession("EXISTING-SESS", "OTHER-APP", "OTHER-INT", "RM-01", start, start.plusMinutes(45)));

        Instant now = Instant.now();
        SchedulingPreviewState preview = createTestPreview("PREV-CONFLICT", "officer.alice", now, now.plus(Duration.ofMinutes(15)), false);

        Map<String, SchedulingPreviewState> map = new HashMap<>();
        map.put("PREV-CONFLICT", preview);
        sessionAttributes.put(OfficerSchedulingController.PREVIEWS_MAP_SESSION_ATTR, map);

        when(req.getPathInfo()).thenReturn("/publish");
        when(req.getParameter("previewId")).thenReturn("PREV-CONFLICT");

        controller.doPost(req, resp);

        // Verification 1: Proposed session was rejected, only existing session remains
        assertEquals(1, sessionRepo.count());
        assertFalse(sessionRepo.existsById("SESS-01"));

        // Verification 2: Application APP-01 remains SHORTLISTED, not SCHEDULED
        Application app = appRepo.findById("APP-01").orElseThrow();
        assertEquals(ApplicationStatus.SHORTLISTED, app.getStatus());

        // Verification 3: Publication summary reports room conflict
        PublicationSummaryState summary = (PublicationSummaryState) sessionAttributes.get(OfficerSchedulingController.PUBLICATION_RESULT_SESSION_ATTR);
        assertNotNull(summary);
        assertEquals(0, summary.publishedCount());
        assertEquals(1, summary.conflictsCount());
        assertEquals(PublicationSummaryState.ConflictCategory.ROOM_OVERLAP, summary.conflictDetails().get(0).category());
    }

    @Test
    @DisplayName("published timetable: filters confirmed appointments by drive ID and date")
    void testPublishedTimetableFiltering() throws ServletException, IOException {
        LocalDateTime dt1 = LocalDateTime.of(2026, 10, 25, 10, 0);
        LocalDateTime dt2 = LocalDateTime.of(2026, 10, 26, 11, 0);

        driveRepo.add(new RecruitmentDrive("DRV-OTHER", "CMP-01", "QA Engineer", 7.0, Set.of("CSE"), LocalDate.now().plusDays(10)));
        appRepo.add(new Application("APP-03", "STU-02", "DRV-OTHER", ApplicationStatus.SCHEDULED));

        sessionRepo.add(new InterviewSession("SESS-A", "APP-01", "INT-01", "RM-01", dt1, dt1.plusMinutes(45)));
        sessionRepo.add(new InterviewSession("SESS-B", "APP-03", "INT-01", "RM-01", dt2, dt2.plusMinutes(45)));

        when(req.getPathInfo()).thenReturn("/published");
        when(req.getParameter("driveId")).thenReturn("DRV-01");
        when(req.getParameter("date")).thenReturn("2026-10-25");

        controller.doGet(req, resp);

        ArgumentCaptor<List<ConfirmedInterviewViewItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(req).setAttribute(eq("confirmedInterviews"), captor.capture());

        List<ConfirmedInterviewViewItem> filtered = captor.getValue();
        assertEquals(1, filtered.size());
        assertEquals("SESS-A", filtered.get(0).sessionId());
        assertEquals("DRV-01", filtered.get(0).driveId());
        assertEquals(LocalDate.of(2026, 10, 25), filtered.get(0).getDate());

        verify(dispatcher).forward(req, resp);
    }
}
