package com.hireflow.web.controller;

import com.hireflow.model.*;
import com.hireflow.repository.*;
import com.hireflow.scheduler.GreedyScheduler;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("OfficerSchedulingControllerTest - Web Scheduling Form and Preview Advisory Generation")
class OfficerSchedulingControllerTest {

    private DriveRepository driveRepo;
    private ApplicationRepository appRepo;
    private StudentRepository studentRepo;
    private InterviewerRepository interviewerRepo;
    private RoomRepository roomRepo;
    private InterviewSessionRepository sessionRepo;
    private CompanyRepository companyRepo;

    private OfficerSchedulingController schedulingController;

    private HttpServletRequest req;
    private HttpServletResponse resp;
    private HttpSession session;
    private RequestDispatcher dispatcher;

    private UserPrincipal officerPrincipal;
    private UserPrincipal studentPrincipal;

    @BeforeEach
    void setUp() {
        driveRepo = new InMemoryDriveRepository();
        appRepo = new InMemoryApplicationRepository();
        studentRepo = new InMemoryStudentRepository();
        interviewerRepo = new InMemoryInterviewerRepository();
        roomRepo = new InMemoryRoomRepository();
        sessionRepo = new InMemoryInterviewSessionRepository();
        companyRepo = new InMemoryCompanyRepository();

        schedulingController = new OfficerSchedulingController(
                driveRepo, appRepo, studentRepo, interviewerRepo, roomRepo, sessionRepo, companyRepo, new GreedyScheduler()
        );

        req = mock(HttpServletRequest.class);
        resp = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        dispatcher = mock(RequestDispatcher.class);

        when(req.getSession(anyBoolean())).thenReturn(session);
        when(req.getSession()).thenReturn(session);
        when(req.getContextPath()).thenReturn("/hireflow");
        when(req.getRequestDispatcher(anyString())).thenReturn(dispatcher);

        officerPrincipal = new UserPrincipal("tpc.officer", UserRole.PLACEMENT_OFFICER, "OFF-01", "Chief Officer");
        studentPrincipal = new UserPrincipal("student.dev", UserRole.STUDENT, "STU-01", "Candidate Dev");

        // Seed domain data
        companyRepo.add(new Company("CMP-GOOGLE", "Google LLC", "Technology"));
        driveRepo.add(new RecruitmentDrive("DRV-GGL-01", "CMP-GOOGLE", "Software Engineer", 8.0, Set.of("CSE"), LocalDate.now().plusDays(10)));

        studentRepo.add(new Student("STU-101", "Alice Smith", "alice@univ.edu", "CSE", 9.0, 2026, true));
        studentRepo.add(new Student("STU-102", "Bob Johnson", "bob@univ.edu", "CSE", 8.8, 2026, true));
        studentRepo.add(new Student("STU-103", "Charlie Rose", "charlie@univ.edu", "CSE", 8.5, 2026, true));

        interviewerRepo.add(new Interviewer("INT-01", "David Evaluator", "CMP-GOOGLE"));
        interviewerRepo.add(new Interviewer("INT-02", "Elena Specialist", "CMP-GOOGLE"));

        roomRepo.add(new Room("RM-A", "Interview Room A", 1));
        roomRepo.add(new Room("RM-B", "Interview Room B", 1));
    }

    @Test
    @DisplayName("doGet: renders scheduling form with drives and shortlisted counts")
    void testSchedulingFormRender() throws ServletException, IOException {
        appRepo.add(new Application("APP-01", "STU-101", "DRV-GGL-01", ApplicationStatus.SHORTLISTED));
        appRepo.add(new Application("APP-02", "STU-102", "DRV-GGL-01", ApplicationStatus.APPLIED));

        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);

        schedulingController.doGet(req, resp);

        verify(req).setAttribute(eq("drives"), anyList());
        verify(req).setAttribute(eq("interviewers"), anyList());
        verify(req).setAttribute(eq("rooms"), anyList());
        verify(req).setAttribute(eq("shortlistedCounts"), anyMap());
        verify(dispatcher).forward(req, resp);
    }

    @Test
    @DisplayName("security: student role and anonymous users are denied access to scheduling screen")
    void testAuthorizationGuards() throws ServletException, IOException {
        // 1. Student principal
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(studentPrincipal);
        schedulingController.doGet(req, resp);
        verify(resp).sendError(eq(HttpServletResponse.SC_FORBIDDEN), contains("Officer"));

        // 2. Anonymous session
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(null);
        reset(resp);
        schedulingController.doGet(req, resp);
        verify(resp).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    }

    @Test
    @DisplayName("security: missing or invalid CSRF token on preview POST is rejected with 403")
    void testPreviewMissingCsrf() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("secret-csrf");
        when(req.getParameter("_csrf")).thenReturn("bad-csrf");
        when(req.getPathInfo()).thenReturn("/preview");

        schedulingController.doPost(req, resp);
        verify(resp).sendError(eq(HttpServletResponse.SC_FORBIDDEN), contains("CSRF"));
    }

    @Test
    @DisplayName("preview: generates proposed schedule for shortlisted candidates without altering database")
    void testSchedulingPreviewShortlistedCandidates() throws ServletException, IOException {
        // Setup 2 shortlisted candidates and 1 applied (non-shortlisted)
        appRepo.add(new Application("APP-01", "STU-101", "DRV-GGL-01", ApplicationStatus.SHORTLISTED));
        appRepo.add(new Application("APP-02", "STU-102", "DRV-GGL-01", ApplicationStatus.SHORTLISTED));
        appRepo.add(new Application("APP-03", "STU-103", "DRV-GGL-01", ApplicationStatus.APPLIED)); // must be excluded

        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-valid");
        when(req.getParameter("_csrf")).thenReturn("csrf-valid");
        when(req.getPathInfo()).thenReturn("/preview");

        when(req.getParameter("driveId")).thenReturn("DRV-GGL-01");
        when(req.getParameter("date")).thenReturn(LocalDate.now().plusDays(2).toString());
        when(req.getParameter("startTime")).thenReturn("09:00");
        when(req.getParameter("endTime")).thenReturn("17:00");
        when(req.getParameter("duration")).thenReturn("45");
        when(req.getParameter("buffer")).thenReturn("15");

        schedulingController.doPost(req, resp);

        // Verify preview state in session and request
        ArgumentCaptor<SchedulingPreviewState> previewCaptor = ArgumentCaptor.forClass(SchedulingPreviewState.class);
        verify(session).setAttribute(eq(OfficerSchedulingController.PREVIEW_SESSION_ATTR), previewCaptor.capture());

        SchedulingPreviewState preview = previewCaptor.getValue();
        assertNotNull(preview);
        assertEquals("DRV-GGL-01", preview.driveId());
        assertEquals(2, preview.totalRequested(), "Only the 2 SHORTLISTED applications must be evaluated");
        assertEquals(2, preview.totalScheduled());
        assertEquals(0, preview.totalUnscheduled());
        assertEquals(2, preview.proposedSessions().size());

        // Invariant: DATABASE MUST REMAIN UNTOUCHED
        assertEquals(0, sessionRepo.count(), "Preview must NEVER insert records into interview_sessions");
        assertEquals(ApplicationStatus.SHORTLISTED, appRepo.findById("APP-01").get().getStatus(), "Application status must remain SHORTLISTED");
        assertEquals(ApplicationStatus.SHORTLISTED, appRepo.findById("APP-02").get().getStatus(), "Application status must remain SHORTLISTED");
        assertEquals(ApplicationStatus.APPLIED, appRepo.findById("APP-03").get().getStatus(), "Non-shortlisted application must remain APPLIED");
    }

    @Test
    @DisplayName("preview: respects existing confirmed appointments from sessionRepository")
    void testPreviewRespectsExistingConfirmedAppointments() throws ServletException, IOException {
        appRepo.add(new Application("APP-01", "STU-101", "DRV-GGL-01", ApplicationStatus.SHORTLISTED));

        LocalDate schedDate = LocalDate.now().plusDays(3);
        LocalDateTime slotStart = LocalDateTime.of(schedDate, LocalTime.of(9, 0));
        LocalDateTime slotEnd = slotStart.plusMinutes(45);

        // Pre-existing confirmed appointment occupies INT-01 and RM-A at 09:00 - 09:45
        sessionRepo.add(new InterviewSession("SES-EXIST-01", "APP-OTHER", "INT-01", "RM-A", slotStart, slotEnd));

        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-valid");
        when(req.getParameter("_csrf")).thenReturn("csrf-valid");
        when(req.getPathInfo()).thenReturn("/preview");

        when(req.getParameter("driveId")).thenReturn("DRV-GGL-01");
        when(req.getParameter("date")).thenReturn(schedDate.toString());
        when(req.getParameter("startTime")).thenReturn("09:00");
        when(req.getParameter("endTime")).thenReturn("17:00");
        when(req.getParameter("duration")).thenReturn("45");
        when(req.getParameter("buffer")).thenReturn("15");

        schedulingController.doPost(req, resp);

        ArgumentCaptor<SchedulingPreviewState> previewCaptor = ArgumentCaptor.forClass(SchedulingPreviewState.class);
        verify(session).setAttribute(eq(OfficerSchedulingController.PREVIEW_SESSION_ATTR), previewCaptor.capture());
        SchedulingPreviewState preview = previewCaptor.getValue();

        assertEquals(1, preview.totalScheduled());
        SchedulingPreviewState.ProposedSessionDto proposed = preview.proposedSessions().get(0);

        // Greedy scheduler should assign INT-02 / RM-B at 09:00 OR assign INT-01 after buffer
        boolean usesDifferentResourceOrSlot = !proposed.interviewerId().equals("INT-01") || !proposed.roomId().equals("RM-A") || proposed.startTime().isAfter(slotEnd);
        assertTrue(usesDifferentResourceOrSlot, "Proposed session must not collide with existing confirmed appointment");
    }

    @Test
    @DisplayName("preview: reports unscheduled candidates when capacity is exceeded")
    void testPreviewReportsUnscheduledWhenCapacityExceeded() throws ServletException, IOException {
        // 3 shortlisted candidates
        appRepo.add(new Application("APP-01", "STU-101", "DRV-GGL-01", ApplicationStatus.SHORTLISTED));
        appRepo.add(new Application("APP-02", "STU-102", "DRV-GGL-01", ApplicationStatus.SHORTLISTED));
        appRepo.add(new Application("APP-03", "STU-103", "DRV-GGL-01", ApplicationStatus.SHORTLISTED));

        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-valid");
        when(req.getParameter("_csrf")).thenReturn("csrf-valid");
        when(req.getPathInfo()).thenReturn("/preview");

        // Small 1-hour window (09:00 - 10:00) with only 1 room and 1 interviewer selected
        when(req.getParameter("driveId")).thenReturn("DRV-GGL-01");
        when(req.getParameter("date")).thenReturn(LocalDate.now().plusDays(1).toString());
        when(req.getParameter("startTime")).thenReturn("09:00");
        when(req.getParameter("endTime")).thenReturn("10:00");
        when(req.getParameter("duration")).thenReturn("45");
        when(req.getParameter("buffer")).thenReturn("15");
        when(req.getParameterValues("interviewerIds")).thenReturn(new String[]{"INT-01"});
        when(req.getParameterValues("roomIds")).thenReturn(new String[]{"RM-A"});

        schedulingController.doPost(req, resp);

        ArgumentCaptor<SchedulingPreviewState> previewCaptor = ArgumentCaptor.forClass(SchedulingPreviewState.class);
        verify(session).setAttribute(eq(OfficerSchedulingController.PREVIEW_SESSION_ATTR), previewCaptor.capture());
        SchedulingPreviewState preview = previewCaptor.getValue();

        assertEquals(3, preview.totalRequested());
        assertEquals(1, preview.totalScheduled()); // only 1 fits in a 1-hour window with 45m+15m
        assertEquals(2, preview.totalUnscheduled());
        assertEquals(2, preview.unscheduledCandidates().size());

        assertTrue(preview.unscheduledCandidates().get(0).reason().contains("capacity")
                || preview.unscheduledCandidates().get(0).reason().contains("occupied")
                || preview.unscheduledCandidates().get(0).reason().contains("slots"));
    }

    @Test
    @DisplayName("preview: rejects preview when drive has zero shortlisted candidates")
    void testPreviewRejectsZeroShortlisted() throws ServletException, IOException {
        // Only APPLIED candidate, zero SHORTLISTED
        appRepo.add(new Application("APP-01", "STU-101", "DRV-GGL-01", ApplicationStatus.APPLIED));

        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-valid");
        when(req.getParameter("_csrf")).thenReturn("csrf-valid");
        when(req.getPathInfo()).thenReturn("/preview");

        when(req.getParameter("driveId")).thenReturn("DRV-GGL-01");
        when(req.getParameter("date")).thenReturn(LocalDate.now().plusDays(1).toString());
        when(req.getParameter("startTime")).thenReturn("09:00");
        when(req.getParameter("endTime")).thenReturn("17:00");
        when(req.getParameter("duration")).thenReturn("45");
        when(req.getParameter("buffer")).thenReturn("15");

        schedulingController.doPost(req, resp);

        verify(session).setAttribute(eq("flash_error"), contains("SHORTLISTED"));
        verify(resp).sendRedirect(contains("/officer/scheduling?driveId=DRV-GGL-01"));
    }
}
