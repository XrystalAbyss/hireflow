package com.hireflow.web.controller;

import com.hireflow.model.*;
import com.hireflow.repository.*;
import com.hireflow.web.dto.ConfirmedInterviewViewItem;
import com.hireflow.web.filter.PortalAuthorizationFilter;
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
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("StudentInterviewControllerTest - Candidate Appointment Visibility and Isolation")
class StudentInterviewControllerTest {

    private InMemoryInterviewSessionRepository sessionRepo;
    private InMemoryApplicationRepository appRepo;
    private InMemoryStudentRepository studentRepo;
    private InMemoryDriveRepository driveRepo;
    private InMemoryCompanyRepository companyRepo;
    private InMemoryRoomRepository roomRepo;
    private InMemoryInterviewerRepository interviewerRepo;

    private StudentInterviewController controller;

    private HttpServletRequest req;
    private HttpServletResponse resp;
    private HttpSession session;
    private RequestDispatcher dispatcher;

    private UserPrincipal studentAlicePrincipal;
    private UserPrincipal studentBobPrincipal;
    private UserPrincipal officerPrincipal;

    @BeforeEach
    void setUp() {
        appRepo = new InMemoryApplicationRepository();
        sessionRepo = new InMemoryInterviewSessionRepository(appRepo);
        studentRepo = new InMemoryStudentRepository();
        driveRepo = new InMemoryDriveRepository();
        companyRepo = new InMemoryCompanyRepository();
        roomRepo = new InMemoryRoomRepository();
        interviewerRepo = new InMemoryInterviewerRepository();

        controller = new StudentInterviewController(
                sessionRepo, appRepo, studentRepo, driveRepo, companyRepo, roomRepo, interviewerRepo
        );

        req = mock(HttpServletRequest.class);
        resp = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        dispatcher = mock(RequestDispatcher.class);

        when(req.getSession(anyBoolean())).thenReturn(session);
        when(req.getRequestDispatcher(anyString())).thenReturn(dispatcher);

        studentAlicePrincipal = new UserPrincipal("alice.smith", UserRole.STUDENT, "Alice Smith", "STU-01");
        studentBobPrincipal = new UserPrincipal("bob.jones", UserRole.STUDENT, "Bob Jones", "STU-02");
        officerPrincipal = new UserPrincipal("officer.lee", UserRole.PLACEMENT_OFFICER, "Officer Lee", null);

        // Seed domain data
        companyRepo.add(new Company("CMP-01", "Acme Tech", "Software"));
        driveRepo.add(new RecruitmentDrive("DRV-01", "CMP-01", "Software Engineer", 8.0, Set.of("CSE"), LocalDate.now().plusDays(5)));

        studentRepo.add(new Student("STU-01", "Alice Smith", "alice@univ.edu", "CSE", 9.0, 2026, true));
        studentRepo.add(new Student("STU-02", "Bob Jones", "bob@univ.edu", "CSE", 8.5, 2026, true));

        interviewerRepo.add(new Interviewer("INT-01", "Dr. Turing", "CMP-01"));
        roomRepo.add(new Room("RM-101", "Lab 1", 1));

        appRepo.add(new Application("APP-01", "STU-01", "DRV-01", ApplicationStatus.SCHEDULED));
        appRepo.add(new Application("APP-02", "STU-02", "DRV-01", ApplicationStatus.SCHEDULED));

        LocalDateTime now = LocalDateTime.of(2026, 10, 25, 10, 0);
        sessionRepo.addWithStudentId(new InterviewSession("SESS-01", "APP-01", "INT-01", "RM-101", now, now.plusMinutes(45)), "STU-01");
        sessionRepo.addWithStudentId(new InterviewSession("SESS-02", "APP-02", "INT-01", "RM-101", now.plusHours(1), now.plusHours(1).plusMinutes(45)), "STU-02");
    }

    @Test
    @DisplayName("doGet: student views only their own confirmed interview appointment")
    void testStudentViewsOnlyTheirOwnInterview() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(studentAlicePrincipal);

        controller.doGet(req, resp);

        ArgumentCaptor<List<ConfirmedInterviewViewItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(req).setAttribute(eq("interviews"), captor.capture());

        List<ConfirmedInterviewViewItem> items = captor.getValue();
        assertEquals(1, items.size(), "Alice should only see her 1 confirmed interview");
        ConfirmedInterviewViewItem item = items.get(0);
        assertEquals("SESS-01", item.sessionId());
        assertEquals("APP-01", item.applicationId());
        assertEquals("STU-01", item.studentId());
        assertEquals("Alice Smith", item.studentName());
        assertEquals("Acme Tech", item.companyName());
        assertEquals("Software Engineer", item.jobTitle());
        assertEquals("Dr. Turing", item.interviewerName());
        assertEquals("Lab 1", item.roomName());

        verify(resp).setHeader(eq("Cache-Control"), contains("no-store"));
        verify(dispatcher).forward(req, resp);
    }

    @Test
    @DisplayName("doGet: candidate Bob sees his own appointment, not Alice's")
    void testCandidateBobSeesOnlyHisAppointment() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(studentBobPrincipal);

        controller.doGet(req, resp);

        ArgumentCaptor<List<ConfirmedInterviewViewItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(req).setAttribute(eq("interviews"), captor.capture());

        List<ConfirmedInterviewViewItem> items = captor.getValue();
        assertEquals(1, items.size());
        assertEquals("SESS-02", items.get(0).sessionId());
        assertEquals("STU-02", items.get(0).studentId());
    }

    @Test
    @DisplayName("doGet: officer account is rejected with 403 Forbidden")
    void testOfficerAccountDeniedAccess() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);

        controller.doGet(req, resp);

        verify(resp).sendError(eq(HttpServletResponse.SC_FORBIDDEN), contains("student"));
        verify(dispatcher, never()).forward(req, resp);
    }

    @Test
    @DisplayName("doGet: unauthenticated request is rejected with 403 Forbidden")
    void testUnauthenticatedAccessDenied() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(null);

        controller.doGet(req, resp);

        verify(resp).sendError(eq(HttpServletResponse.SC_FORBIDDEN), contains("student"));
        verify(dispatcher, never()).forward(req, resp);
    }

    @Test
    @DisplayName("doGet: student with no scheduled interviews receives empty list")
    void testStudentWithNoInterviewsReceivesEmptyList() throws ServletException, IOException {
        UserPrincipal studentNoInterviews = new UserPrincipal("carol.white", UserRole.STUDENT, "Carol White", "STU-99");
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(studentNoInterviews);

        controller.doGet(req, resp);

        ArgumentCaptor<List<ConfirmedInterviewViewItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(req).setAttribute(eq("interviews"), captor.capture());
        assertTrue(captor.getValue().isEmpty(), "Student with no scheduled interviews must receive empty list");
    }
}
