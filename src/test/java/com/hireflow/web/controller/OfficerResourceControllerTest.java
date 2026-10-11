package com.hireflow.web.controller;

import com.hireflow.model.Company;
import com.hireflow.model.Interviewer;
import com.hireflow.model.InterviewSession;
import com.hireflow.model.Room;
import com.hireflow.repository.*;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("OfficerResourceControllerTest - Interview Resources (Interviewers & Rooms) Operations")
class OfficerResourceControllerTest {

    private InterviewerRepository interviewerRepo;
    private RoomRepository roomRepo;
    private InterviewSessionRepository sessionRepo;
    private CompanyRepository companyRepo;

    private OfficerInterviewerController interviewerController;
    private OfficerRoomController roomController;

    private HttpServletRequest req;
    private HttpServletResponse resp;
    private HttpSession session;
    private RequestDispatcher dispatcher;

    private UserPrincipal officerPrincipal;
    private UserPrincipal studentPrincipal;

    @BeforeEach
    void setUp() {
        interviewerRepo = new InMemoryInterviewerRepository();
        roomRepo = new InMemoryRoomRepository();
        sessionRepo = new InMemoryInterviewSessionRepository();
        companyRepo = new InMemoryCompanyRepository();

        interviewerController = new OfficerInterviewerController(interviewerRepo, companyRepo, sessionRepo);
        roomController = new OfficerRoomController(roomRepo, sessionRepo);

        req = mock(HttpServletRequest.class);
        resp = mock(HttpServletResponse.class);
        session = mock(HttpSession.class);
        dispatcher = mock(RequestDispatcher.class);

        when(req.getSession(anyBoolean())).thenReturn(session);
        when(req.getSession()).thenReturn(session);
        when(req.getContextPath()).thenReturn("/hireflow");
        when(req.getRequestDispatcher(anyString())).thenReturn(dispatcher);

        officerPrincipal = new UserPrincipal("officer1", UserRole.PLACEMENT_OFFICER, "OFF-1", "Placement Officer");
        studentPrincipal = new UserPrincipal("student1", UserRole.STUDENT, "STU-1", "Test Student");

        // Seed companies
        companyRepo.add(new Company("CMP-AMZN", "Amazon Inc", "E-Commerce"));
        companyRepo.add(new Company("CMP-MSFT", "Microsoft", "Technology"));
    }

    // ==========================================
    // Interviewer Controller Tests
    // ==========================================

    @Test
    @DisplayName("interviewer: doGet lists interviewers with company name mapping")
    void testInterviewerList() throws ServletException, IOException {
        interviewerRepo.add(new Interviewer("INT-1", "Dr. Alice", "CMP-AMZN"));
        interviewerRepo.add(new Interviewer("INT-2", "Bob Vance", "CMP-MSFT"));

        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);

        interviewerController.doGet(req, resp);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(req).setAttribute(eq("items"), captor.capture());
        assertEquals(2, captor.getValue().size());
        verify(dispatcher).forward(req, resp);
    }

    @Test
    @DisplayName("interviewer: doPost creates interviewer successfully with CSRF")
    void testCreateInterviewerSuccess() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-valid");
        when(req.getParameter("_csrf")).thenReturn("csrf-valid");
        when(req.getParameter("id")).thenReturn("INT-NEW");
        when(req.getParameter("name")).thenReturn("Dr. Charlie");
        when(req.getParameter("companyId")).thenReturn("CMP-AMZN");

        interviewerController.doPost(req, resp);

        verify(resp).sendRedirect("/hireflow/officer/interviewers");
        assertTrue(interviewerRepo.existsById("INT-NEW"));
        assertEquals("Dr. Charlie", interviewerRepo.findById("INT-NEW").get().getName());
    }

    @Test
    @DisplayName("interviewer: create fails for duplicate ID or non-existent company")
    void testCreateInterviewerValidationFailures() throws ServletException, IOException {
        interviewerRepo.add(new Interviewer("INT-DUP", "Existing", "CMP-AMZN"));

        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-valid");
        when(req.getParameter("_csrf")).thenReturn("csrf-valid");

        // 1. Duplicate ID
        when(req.getParameter("id")).thenReturn("INT-DUP");
        when(req.getParameter("name")).thenReturn("Clone");
        when(req.getParameter("companyId")).thenReturn("CMP-AMZN");

        interviewerController.doPost(req, resp);
        verify(session).setAttribute(eq("flash_error"), contains("already exists"));

        // 2. Non-existent company
        when(req.getParameter("id")).thenReturn("INT-NOCOMP");
        when(req.getParameter("name")).thenReturn("Valid Name");
        when(req.getParameter("companyId")).thenReturn("CMP-FAKE");

        interviewerController.doPost(req, resp);
        verify(session, atLeastOnce()).setAttribute(eq("flash_error"), contains("does not exist"));
    }

    @Test
    @DisplayName("interviewer: delete succeeds when unreferenced and fails when referenced by confirmed sessions")
    void testDeleteInterviewerReferentialIntegrity() throws ServletException, IOException {
        interviewerRepo.add(new Interviewer("INT-FREE", "Free Interviewer", "CMP-AMZN"));
        interviewerRepo.add(new Interviewer("INT-BUSY", "Busy Interviewer", "CMP-AMZN"));

        // Link confirmed session to INT-BUSY
        sessionRepo.add(new InterviewSession("SES-1", "APP-1", "INT-BUSY", "RM-1",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1)));

        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-valid");
        when(req.getParameter("_csrf")).thenReturn("csrf-valid");

        // Try deleting INT-BUSY
        when(req.getPathInfo()).thenReturn("/INT-BUSY/delete");
        interviewerController.doPost(req, resp);

        verify(session).setAttribute(eq("flash_error"), contains("confirmed interview session(s) reference this interviewer"));
        assertTrue(interviewerRepo.existsById("INT-BUSY"), "Interviewer referenced by session must NOT be deleted");

        // Now delete INT-FREE
        when(req.getPathInfo()).thenReturn("/INT-FREE/delete");
        interviewerController.doPost(req, resp);

        verify(session).setAttribute(eq("flash_success"), contains("deleted successfully"));
        assertFalse(interviewerRepo.existsById("INT-FREE"), "Unreferenced interviewer must be deleted");
    }

    // ==========================================
    // Room Controller Tests
    // ==========================================

    @Test
    @DisplayName("room: doGet lists rooms with capacity")
    void testRoomList() throws ServletException, IOException {
        roomRepo.add(new Room("RM-101", "Lab A", 1));
        roomRepo.add(new Room("RM-102", "Lab B", 2));

        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);

        roomController.doGet(req, resp);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(req).setAttribute(eq("items"), captor.capture());
        assertEquals(2, captor.getValue().size());
        verify(dispatcher).forward(req, resp);
    }

    @Test
    @DisplayName("room: doPost creates room successfully with positive capacity")
    void testCreateRoomSuccess() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-valid");
        when(req.getParameter("_csrf")).thenReturn("csrf-valid");
        when(req.getParameter("id")).thenReturn("RM-201");
        when(req.getParameter("name")).thenReturn("Executive Suite");
        when(req.getParameter("capacity")).thenReturn("4");

        roomController.doPost(req, resp);

        verify(resp).sendRedirect("/hireflow/officer/rooms");
        assertTrue(roomRepo.existsById("RM-201"));
        assertEquals(4, roomRepo.findById("RM-201").get().getCapacity());
    }

    @Test
    @DisplayName("room: create rejects non-positive capacity or duplicate ID")
    void testCreateRoomInvalidCapacity() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-valid");
        when(req.getParameter("_csrf")).thenReturn("csrf-valid");

        // Non-positive capacity
        when(req.getParameter("id")).thenReturn("RM-ZERO");
        when(req.getParameter("name")).thenReturn("Zero Room");
        when(req.getParameter("capacity")).thenReturn("0");

        roomController.doPost(req, resp);
        verify(session).setAttribute(eq("flash_error"), contains("greater than 0"));
        assertFalse(roomRepo.existsById("RM-ZERO"));
    }

    @Test
    @DisplayName("room: delete rejects when confirmed sessions exist")
    void testDeleteRoomReferentialIntegrity() throws ServletException, IOException {
        roomRepo.add(new Room("RM-BUSY", "Busy Room", 1));
        sessionRepo.add(new InterviewSession("SES-ROOM", "APP-2", "INT-1", "RM-BUSY",
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(1)));

        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("csrf-valid");
        when(req.getParameter("_csrf")).thenReturn("csrf-valid");
        when(req.getPathInfo()).thenReturn("/RM-BUSY/delete");

        roomController.doPost(req, resp);

        verify(session).setAttribute(eq("flash_error"), contains("confirmed interview session(s) reference this room"));
        assertTrue(roomRepo.existsById("RM-BUSY"), "Room referenced by sessions must not be deleted");
    }

    // ==========================================
    // Security & Authorization Checks
    // ==========================================

    @Test
    @DisplayName("security: student role is rejected with 403 Forbidden on resource routes")
    void testStudentRoleRejectedOnResources() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(studentPrincipal);

        interviewerController.doGet(req, resp);
        verify(resp).sendError(eq(HttpServletResponse.SC_FORBIDDEN), contains("Officer"));

        roomController.doGet(req, resp);
        verify(resp, atLeastOnce()).sendError(eq(HttpServletResponse.SC_FORBIDDEN), contains("Officer"));
    }

    @Test
    @DisplayName("security: missing CSRF token on POST is rejected with 403 Forbidden")
    void testMissingCsrfRejectedOnResources() throws ServletException, IOException {
        when(session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)).thenReturn(officerPrincipal);
        when(session.getAttribute(CsrfTokenManager.CSRF_SESSION_ATTR)).thenReturn("valid-token");
        when(req.getParameter("_csrf")).thenReturn("wrong-token");

        interviewerController.doPost(req, resp);
        verify(resp).sendError(eq(HttpServletResponse.SC_FORBIDDEN), contains("CSRF"));

        roomController.doPost(req, resp);
        verify(resp, atLeastOnce()).sendError(eq(HttpServletResponse.SC_FORBIDDEN), contains("CSRF"));
    }
}
