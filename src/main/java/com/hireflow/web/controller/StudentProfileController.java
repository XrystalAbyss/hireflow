package com.hireflow.web.controller;

import com.hireflow.model.Student;
import com.hireflow.service.StudentService;
import com.hireflow.web.filter.PortalAuthorizationFilter;
import com.hireflow.web.listener.WebContextListener;
import com.hireflow.web.security.UserPrincipal;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Controller handling authenticated student profile inspection (GET /student/profile).
 * <p>
 * Security invariants:
 * <ul>
 *     <li>Derives student ID strictly from the authenticated {@link UserPrincipal} in session.</li>
 *     <li>Never accepts or trusts client-supplied query parameters for student identity.</li>
 *     <li>Enforces strict anti-caching response headers on private profile data.</li>
 *     <li>Read-only view without client-controlled mutation pathways in this milestone.</li>
 * </ul>
 */
public class StudentProfileController extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(StudentProfileController.class.getName());
    private static final String PROFILE_VIEW = "/WEB-INF/views/student/profile.jsp";

    private StudentService studentService;

    public StudentProfileController() {
    }

    public StudentProfileController(StudentService studentService) {
        this.studentService = studentService;
    }

    @Override
    public void init() {
        Object attr = getServletContext().getAttribute(WebContextListener.STUDENT_SERVICE_ATTR);
        if (attr instanceof StudentService) {
            this.studentService = (StudentService) attr;
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // Enforce anti-caching headers on private academic records
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);

        HttpSession session = req.getSession(false);
        if (session == null) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        UserPrincipal principal = (UserPrincipal) session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR);
        if (principal == null || !principal.isStudent() || principal.getStudentId() == null) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Candidate profile requires an authenticated STUDENT session.");
            return;
        }

        String authoritativeStudentId = principal.getStudentId();
        StudentService service = resolveStudentService();
        Optional<Student> optStudent = service.getStudentById(authoritativeStudentId);

        if (optStudent.isEmpty()) {
            LOGGER.warning("[StudentPortal] Record not found for linked student ID: " + authoritativeStudentId);
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Student academic profile record could not be found.");
            return;
        }

        Student student = optStudent.get();
        req.setAttribute("student", student);
        req.setAttribute("principal", principal);
        req.setAttribute("pageTitle", "My Academic Profile");
        req.setAttribute("activeNav", "profile");

        req.getRequestDispatcher(PROFILE_VIEW).forward(req, resp);
    }

    private StudentService resolveStudentService() {
        if (this.studentService != null) {
            return this.studentService;
        }
        Object attr = getServletContext().getAttribute(WebContextListener.STUDENT_SERVICE_ATTR);
        if (attr instanceof StudentService) {
            this.studentService = (StudentService) attr;
            return this.studentService;
        }
        throw new IllegalStateException("StudentService is not available in ServletContext");
    }
}
