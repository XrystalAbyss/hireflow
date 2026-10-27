package com.hireflow.web.controller;

import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.Application;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Student;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.RecruitmentDriveService;
import com.hireflow.service.StudentService;
import com.hireflow.web.dto.StudentApplicationViewItem;
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
import java.net.URI;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Controller managing student drive application submissions and tracking
 * (GET /student/applications and POST /student/applications).
 * <p>
 * Security invariants:
 * <ul>
 *     <li>Derives student ID strictly from authenticated {@link UserPrincipal}; ignores any client-supplied IDs.</li>
 *     <li>Requires cryptographically valid CSRF token validation on all POST submissions.</li>
 *     <li>Re-evaluates academic eligibility and prevents duplicates at the transaction boundary.</li>
 *     <li>Employs Post/Redirect/Get (PRG) to prevent accidental resubmission on page refreshes.</li>
 *     <li>Does not leak internal SQL syntax errors, database constraints, or officer notes to candidates.</li>
 *     <li>Enforces strict anti-caching response headers on private application records.</li>
 * </ul>
 */
public class StudentApplicationController extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(StudentApplicationController.class.getName());
    private static final String APPLICATIONS_VIEW = "/WEB-INF/views/student/applications.jsp";

    private StudentService studentService;
    private RecruitmentDriveService driveService;
    private ApplicationService applicationService;
    private CompanyRepository companyRepository;

    public StudentApplicationController() {
    }

    public StudentApplicationController(StudentService studentService,
                                        RecruitmentDriveService driveService,
                                        ApplicationService applicationService,
                                        CompanyRepository companyRepository) {
        this.studentService = studentService;
        this.driveService = driveService;
        this.applicationService = applicationService;
        this.companyRepository = companyRepository;
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
        if (sc.getAttribute(WebContextListener.COMPANY_REPO_ATTR) instanceof CompanyRepository cr) {
            this.companyRepository = cr;
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
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
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Application tracking requires an authenticated STUDENT session.");
            return;
        }

        String studentId = principal.getStudentId();
        StudentService sService = resolveStudentService();
        Optional<Student> optStudent = sService.getStudentById(studentId);
        if (optStudent.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Student record could not be found.");
            return;
        }

        Student student = optStudent.get();
        ApplicationService aService = resolveApplicationService();
        RecruitmentDriveService dService = resolveDriveService();
        CompanyRepository cRepo = resolveCompanyRepository();

        // 1. Fetch student applications
        List<Application> applications = aService.getApplicationsForStudent(studentId);

        // 2. Batch load drives and companies to avoid N+1 queries
        Map<String, RecruitmentDrive> driveMap = dService.getAllDrives().stream()
                .collect(Collectors.toMap(RecruitmentDrive::getId, Function.identity(), (d1, d2) -> d1));
        Map<String, Company> companyMap = cRepo.findAll().stream()
                .collect(Collectors.toMap(Company::getId, Function.identity(), (c1, c2) -> c1));

        // 3. Map into view items
        List<StudentApplicationViewItem> items = new ArrayList<>();
        for (Application app : applications) {
            RecruitmentDrive drive = driveMap.get(app.getDriveId());
            String jobTitle = (drive != null) ? drive.getJobTitle() : "Placement Drive";
            String companyName = "Corporate Partner";
            if (drive != null) {
                Company company = companyMap.get(drive.getCompanyId());
                if (company != null) {
                    companyName = company.getName();
                }
            }
            items.add(StudentApplicationViewItem.from(app, companyName, jobTitle));
        }

        // Sort applications: most recent first
        items.sort(Comparator.comparing(
                (StudentApplicationViewItem it) -> it.application().getAppliedAt(),
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        // Transfer PRG flash messages from session to request
        Object flashMsg = session.getAttribute("flashMessage");
        Object flashType = session.getAttribute("flashType");
        if (flashMsg != null) {
            req.setAttribute("flashMessage", flashMsg);
            req.setAttribute("flashType", flashType != null ? flashType : "info");
            session.removeAttribute("flashMessage");
            session.removeAttribute("flashType");
        }

        req.setAttribute("student", student);
        req.setAttribute("principal", principal);
        req.setAttribute("applicationItems", items);
        req.setAttribute("pageTitle", "My Submitted Applications");
        req.setAttribute("activeNav", "applications");

        req.getRequestDispatcher(APPLICATIONS_VIEW).forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);

        // 1. Enforce CSRF token verification
        if (!CsrfTokenManager.validateToken(req)) {
            LOGGER.warning("[Security] Rejected POST /student/applications with missing or invalid CSRF token from IP: " + req.getRemoteAddr());
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF security token.");
            return;
        }

        // 2. Validate Origin / Referer header (defense in depth)
        if (!isValidOrigin(req)) {
            LOGGER.warning("[Security] Rejected POST /student/applications due to Origin/Referer header mismatch from IP: " + req.getRemoteAddr());
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Cross-origin request blocked by security policy.");
            return;
        }

        // 3. Verify authenticated STUDENT principal
        HttpSession session = req.getSession(false);
        if (session == null) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        UserPrincipal principal = (UserPrincipal) session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR);
        if (principal == null || !principal.isStudent() || principal.getStudentId() == null) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Submitting applications requires an authenticated STUDENT session.");
            return;
        }

        // 4. Derive student identity AUTHORITATIVELY from session principal
        // DO NOT accept or trust any submitted 'studentId' parameter from the form!
        String authoritativeStudentId = principal.getStudentId();

        // 5. Extract and validate drive ID parameter
        String rawDriveId = req.getParameter("driveId");
        if (rawDriveId == null || rawDriveId.trim().isEmpty() || rawDriveId.trim().length() > 32) {
            session.setAttribute("flashType", "error");
            session.setAttribute("flashMessage", "Invalid recruitment drive identifier.");
            resp.sendRedirect(req.getContextPath() + "/student/drives");
            return;
        }

        String driveId = rawDriveId.trim().toUpperCase();

        try {
            ApplicationService aService = resolveApplicationService();
            Application app = aService.submitApplication(authoritativeStudentId, driveId);

            LOGGER.info(String.format("[StudentPortal] Application %s submitted successfully for student %s on drive %s",
                    app.getId(), authoritativeStudentId, driveId));

            // Post/Redirect/Get to prevent form resubmission
            session.setAttribute("flashType", "success");
            session.setAttribute("flashMessage", "Application submitted successfully! Your application reference is " + app.getId() + ".");
            resp.sendRedirect(req.getContextPath() + "/student/applications");

        } catch (DuplicateEntityException ex) {
            LOGGER.warning(String.format("[StudentPortal] Duplicate application rejected for student %s on drive %s",
                    authoritativeStudentId, driveId));
            session.setAttribute("flashType", "warn");
            session.setAttribute("flashMessage", "You have already applied for this recruitment drive.");
            resp.sendRedirect(req.getContextPath() + "/student/drives");

        } catch (ValidationException ex) {
            LOGGER.warning(String.format("[StudentPortal] Application eligibility rejection for student %s on drive %s: %s",
                    authoritativeStudentId, driveId, ex.getMessage()));
            session.setAttribute("flashType", "error");
            session.setAttribute("flashMessage", ex.getMessage());
            resp.sendRedirect(req.getContextPath() + "/student/drives");

        } catch (EntityNotFoundException ex) {
            LOGGER.warning(String.format("[StudentPortal] Drive or Student entity not found: %s", ex.getMessage()));
            session.setAttribute("flashType", "error");
            session.setAttribute("flashMessage", "The specified recruitment drive or candidate profile was not found.");
            resp.sendRedirect(req.getContextPath() + "/student/drives");

        } catch (Exception ex) {
            LOGGER.severe(String.format("[StudentPortal] Unexpected error submitting application for student %s on drive %s: %s",
                    authoritativeStudentId, driveId, ex.getMessage()));
            session.setAttribute("flashType", "error");
            session.setAttribute("flashMessage", "Unable to process application submission at this time. Please try again.");
            resp.sendRedirect(req.getContextPath() + "/student/drives");
        }
    }

    private boolean isValidOrigin(HttpServletRequest req) {
        String origin = req.getHeader("Origin");
        if (origin != null && !origin.trim().isEmpty()) {
            try {
                URI originUri = URI.create(origin);
                String originHost = originUri.getHost();
                String serverName = req.getServerName();
                return originHost != null && (originHost.equalsIgnoreCase(serverName) || "localhost".equalsIgnoreCase(originHost) || "127.0.0.1".equals(originHost));
            } catch (Exception ex) {
                return false;
            }
        }
        return true;
    }

    private StudentService resolveStudentService() {
        if (this.studentService != null) return this.studentService;
        if (getServletContext().getAttribute(WebContextListener.STUDENT_SERVICE_ATTR) instanceof StudentService ss) {
            this.studentService = ss;
            return ss;
        }
        throw new IllegalStateException("StudentService is not available in ServletContext");
    }

    private RecruitmentDriveService resolveDriveService() {
        if (this.driveService != null) return this.driveService;
        if (getServletContext().getAttribute(WebContextListener.DRIVE_SERVICE_ATTR) instanceof RecruitmentDriveService ds) {
            this.driveService = ds;
            return ds;
        }
        throw new IllegalStateException("RecruitmentDriveService is not available in ServletContext");
    }

    private ApplicationService resolveApplicationService() {
        if (this.applicationService != null) return this.applicationService;
        if (getServletContext().getAttribute(WebContextListener.APPLICATION_SERVICE_ATTR) instanceof ApplicationService as) {
            this.applicationService = as;
            return as;
        }
        throw new IllegalStateException("ApplicationService is not available in ServletContext");
    }

    private CompanyRepository resolveCompanyRepository() {
        if (this.companyRepository != null) return this.companyRepository;
        if (getServletContext().getAttribute(WebContextListener.COMPANY_REPO_ATTR) instanceof CompanyRepository cr) {
            this.companyRepository = cr;
            return cr;
        }
        throw new IllegalStateException("CompanyRepository is not available in ServletContext");
    }
}
