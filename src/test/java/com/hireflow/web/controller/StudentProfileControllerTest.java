package com.hireflow.web.controller;

import com.hireflow.model.Student;
import com.hireflow.service.StudentService;
import com.hireflow.web.filter.PortalAuthorizationFilter;
import com.hireflow.web.security.UserPrincipal;
import com.hireflow.web.security.UserRole;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.Mockito.*;

@DisplayName("StudentProfileController - Academic Profile Inspection Tests")
class StudentProfileControllerTest {

    private StudentService studentService;
    private StudentProfileController controller;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private HttpSession session;
    private RequestDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        studentService = mock(StudentService.class);
        controller = new StudentProfileController(studentService);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        dispatcher = mock(RequestDispatcher.class);

        when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);
        when(request.getContextPath()).thenReturn("/hireflow");
    }

    @Test
    @DisplayName("profile: loads student record authoritatively from principal in session")
    void testProfileLoadsAuthoritatively() throws Exception {
        UserPrincipal principal = new UserPrincipal("rahul.sharma", UserRole.STUDENT, "Rahul Sharma", "STU-101");
        Student student = new Student("STU-101", "Rahul Sharma", "rahul@example.com", "CSE", 8.85, 2026);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(principal);
        when(studentService.getStudentById("STU-101")).thenReturn(Optional.of(student));

        controller.doGet(request, response);

        verify(response).setHeader(eq("Cache-Control"), contains("no-cache"));
        verify(request).setAttribute(eq("student"), eq(student));
        verify(request).setAttribute(eq("principal"), eq(principal));
        verify(dispatcher).forward(request, response);
    }

    @Test
    @DisplayName("profile: client query parameter ?studentId=STU-999 is completely ignored")
    void testProfileIgnoresTamperedQueryParameter() throws Exception {
        UserPrincipal principal = new UserPrincipal("rahul.sharma", UserRole.STUDENT, "Rahul Sharma", "STU-101");
        Student student = new Student("STU-101", "Rahul Sharma", "rahul@example.com", "CSE", 8.85, 2026);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(principal);
        // Attacker attempts to query another student ID
        when(request.getParameter("studentId")).thenReturn("STU-999");
        when(studentService.getStudentById("STU-101")).thenReturn(Optional.of(student));

        controller.doGet(request, response);

        // Verify StudentService was called with STU-101 from principal, NEVER STU-999
        verify(studentService).getStudentById("STU-101");
        verify(studentService, never()).getStudentById("STU-999");
        verify(request).setAttribute(eq("student"), eq(student));
    }

    @Test
    @DisplayName("profile: returns 403 Forbidden if user is not a student")
    void testProfileRejectsNonStudentPrincipal() throws Exception {
        UserPrincipal officerPrincipal = new UserPrincipal("officer", UserRole.PLACEMENT_OFFICER, "Officer", null);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);

        controller.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(dispatcher, never()).forward(request, response);
    }

    @Test
    @DisplayName("profile: returns 404 Not Found if linked student record does not exist")
    void testProfileHandlesMissingStudentRecord() throws Exception {
        UserPrincipal principal = new UserPrincipal("ghost", UserRole.STUDENT, "Ghost", "STU-GHOST");

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(principal);
        when(studentService.getStudentById("STU-GHOST")).thenReturn(Optional.empty());

        controller.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_NOT_FOUND), anyString());
    }
}
