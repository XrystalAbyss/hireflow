package com.hireflow.web.controller;

import com.hireflow.model.Student;
import com.hireflow.repository.InterviewSessionRepository;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.RecruitmentDriveService;
import com.hireflow.service.StudentService;
import com.hireflow.web.filter.PortalAuthorizationFilter;
import com.hireflow.web.listener.WebContextListener;
import com.hireflow.web.security.CsrfTokenManager;
import com.hireflow.web.security.UserPrincipal;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Optional;

/**
 * Controller serving the authenticated student candidate dashboard (GET /student/dashboard).
 */
public class StudentDashboardController extends HttpServlet {

    private static final String STUDENT_DASHBOARD_VIEW = "/WEB-INF/views/student/dashboard.jsp";

    private StudentService studentService;
    private RecruitmentDriveService driveService;
    private ApplicationService applicationService;
    private com.hireflow.repository.InterviewSessionRepository sessionRepository;

    public StudentDashboardController() {
    }

    public StudentDashboardController(StudentService studentService,
                                      RecruitmentDriveService driveService,
                                      ApplicationService applicationService) {
        this(studentService, driveService, applicationService, null);
    }

    public StudentDashboardController(StudentService studentService,
                                      RecruitmentDriveService driveService,
                                      ApplicationService applicationService,
                                      com.hireflow.repository.InterviewSessionRepository sessionRepository) {
        this.studentService = studentService;
        this.driveService = driveService;
        this.applicationService = applicationService;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public void init() {
        var sc = getServletContext();
        if (sc.getAttribute(WebContextListener.STUDENT_SERVICE_ATTR) instanceof StudentService ss) {
            this.studentService = ss;
        }
        if (sc.getAttribute(WebContextListener.DRIVE_SERVICE_ATTR) instanceof RecruitmentDriveService ds) {
            this.driveService = ds;
        }
        if (sc.getAttribute(WebContextListener.APPLICATION_SERVICE_ATTR) instanceof ApplicationService as) {
            this.applicationService = as;
        }
        if (sc.getAttribute(WebContextListener.SESSION_REPO_ATTR) instanceof com.hireflow.repository.InterviewSessionRepository isr) {
            this.sessionRepository = isr;
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);

        HttpSession session = req.getSession(false);
        UserPrincipal principal = (session != null)
                ? (UserPrincipal) session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)
                : null;

        if (principal == null || !principal.isStudent()) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied to student dashboard.");
            return;
        }

        String studentId = principal.getStudentId();
        Student student = null;
        if (studentId != null) {
            StudentService sService = resolveStudentService();
            if (sService != null) {
                Optional<Student> optStudent = sService.getStudentById(studentId);
                student = optStudent.orElse(null);
            }
        }

        int activeDrivesCount = 0;
        RecruitmentDriveService dService = resolveDriveService();
        if (dService != null) {
            activeDrivesCount = dService.getActiveDrives().size();
        }

        int submittedApplicationsCount = 0;
        ApplicationService aService = resolveApplicationService();
        if (aService != null && studentId != null) {
            submittedApplicationsCount = aService.getApplicationsForStudent(studentId).size();
        }

        int confirmedInterviewsCount = 0;
        InterviewSessionRepository sessRepo = resolveSessionRepository();
        if (sessRepo != null && studentId != null) {
            confirmedInterviewsCount = sessRepo.findByStudentId(studentId).size();
        }

        String csrfToken = CsrfTokenManager.getOrCreateToken(session);
        req.setAttribute("user", principal);
        req.setAttribute("student", student);
        req.setAttribute("activeDrivesCount", activeDrivesCount);
        req.setAttribute("submittedApplicationsCount", submittedApplicationsCount);
        req.setAttribute("confirmedInterviewsCount", confirmedInterviewsCount);
        req.setAttribute("csrfToken", csrfToken);
        req.setAttribute("pageTitle", "Student Portal Dashboard");
        req.setAttribute("activeNav", "dashboard");

        req.getRequestDispatcher(STUDENT_DASHBOARD_VIEW).forward(req, resp);
    }

    private StudentService resolveStudentService() {
        if (this.studentService != null) return this.studentService;
        try {
            if (getServletConfig() != null && getServletContext() != null) {
                if (getServletContext().getAttribute(WebContextListener.STUDENT_SERVICE_ATTR) instanceof StudentService ss) {
                    this.studentService = ss;
                    return ss;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private RecruitmentDriveService resolveDriveService() {
        if (this.driveService != null) return this.driveService;
        try {
            if (getServletConfig() != null && getServletContext() != null) {
                if (getServletContext().getAttribute(WebContextListener.DRIVE_SERVICE_ATTR) instanceof RecruitmentDriveService ds) {
                    this.driveService = ds;
                    return ds;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private ApplicationService resolveApplicationService() {
        if (this.applicationService != null) return this.applicationService;
        try {
            if (getServletConfig() != null && getServletContext() != null) {
                if (getServletContext().getAttribute(WebContextListener.APPLICATION_SERVICE_ATTR) instanceof ApplicationService as) {
                    this.applicationService = as;
                    return as;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private com.hireflow.repository.InterviewSessionRepository resolveSessionRepository() {
        if (this.sessionRepository != null) return this.sessionRepository;
        try {
            if (getServletConfig() != null && getServletContext() != null) {
                if (getServletContext().getAttribute(WebContextListener.SESSION_REPO_ATTR) instanceof com.hireflow.repository.InterviewSessionRepository isr) {
                    this.sessionRepository = isr;
                    return isr;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
