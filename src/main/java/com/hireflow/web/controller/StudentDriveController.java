package com.hireflow.web.controller;

import com.hireflow.model.Application;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Student;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.EligibilityService;
import com.hireflow.service.RecruitmentDriveService;
import com.hireflow.service.StudentService;
import com.hireflow.web.dto.StudentDriveViewItem;
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
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Controller providing candidate-tailored recruitment drives directory with real-time server-side
 * eligibility evaluation (GET /student/drives).
 * <p>
 * Security invariants:
 * <ul>
 *     <li>Student identity is derived exclusively from server-side {@link UserPrincipal}.</li>
 *     <li>Academic eligibility is evaluated server-side using the student's authoritative database record.</li>
 *     <li>Never accepts client parameters representing eligibility outcomes or cutoff thresholds.</li>
 *     <li>Prevents N+1 database queries through batch map indexing.</li>
 *     <li>Enforces strict anti-caching response headers.</li>
 * </ul>
 */
public class StudentDriveController extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(StudentDriveController.class.getName());
    private static final String DRIVES_VIEW = "/WEB-INF/views/student/drives.jsp";

    private StudentService studentService;
    private RecruitmentDriveService driveService;
    private EligibilityService eligibilityService;
    private ApplicationService applicationService;
    private CompanyRepository companyRepository;

    public StudentDriveController() {
    }

    public StudentDriveController(StudentService studentService,
                                  RecruitmentDriveService driveService,
                                  EligibilityService eligibilityService,
                                  ApplicationService applicationService,
                                  CompanyRepository companyRepository) {
        this.studentService = studentService;
        this.driveService = driveService;
        this.eligibilityService = eligibilityService;
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
        if (sc.getAttribute(WebContextListener.ELIGIBILITY_SERVICE_ATTR) instanceof EligibilityService es) {
            this.eligibilityService = es;
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
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: Candidate drive listing requires an authenticated STUDENT session.");
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
        RecruitmentDriveService dService = resolveDriveService();
        EligibilityService eService = resolveEligibilityService();
        ApplicationService aService = resolveApplicationService();
        CompanyRepository cRepo = resolveCompanyRepository();

        // 1. Fetch drives
        List<RecruitmentDrive> allDrives = dService.getAllDrives();

        // 2. Fetch student's existing applications (indexed by driveId to prevent N+1)
        List<Application> studentApps = aService.getApplicationsForStudent(studentId);
        Map<String, Application> appsByDriveId = studentApps.stream()
                .collect(Collectors.toMap(Application::getDriveId, Function.identity(), (a1, a2) -> a1));

        // 3. Batch load companies to prevent N+1 queries
        Map<String, Company> companyMap = cRepo.findAll().stream()
                .collect(Collectors.toMap(Company::getId, Function.identity(), (c1, c2) -> c1));

        // 4. Construct view models with authoritative eligibility evaluation
        List<StudentDriveViewItem> items = new ArrayList<>();
        for (RecruitmentDrive drive : allDrives) {
            String companyName = Optional.ofNullable(companyMap.get(drive.getCompanyId()))
                    .map(Company::getName)
                    .orElse("Corporate Partner");

            Application existingApp = appsByDriveId.get(drive.getId());
            boolean hasApplied = (existingApp != null);
            EligibilityService.EligibilityResult eligibilityResult = eService.checkEligibility(student, drive);

            items.add(new StudentDriveViewItem(drive, companyName, hasApplied, existingApp, eligibilityResult));
        }

        // Sort items: open & eligible first, then already applied, then ineligible, then closed
        items.sort(Comparator
                .comparing((StudentDriveViewItem it) -> !it.isOpen())
                .thenComparing((StudentDriveViewItem it) -> it.hasApplied())
                .thenComparing((StudentDriveViewItem it) -> !it.eligibilityResult().isEligible())
                .thenComparing((StudentDriveViewItem it) -> it.drive().getApplicationDeadline()));

        // Check for flash messages stored in session (PRG support)
        Object flashMsg = session.getAttribute("flashMessage");
        Object flashType = session.getAttribute("flashType");
        if (flashMsg != null) {
            req.setAttribute("flashMessage", flashMsg);
            req.setAttribute("flashType", flashType != null ? flashType : "info");
            session.removeAttribute("flashMessage");
            session.removeAttribute("flashType");
        }

        String csrfToken = CsrfTokenManager.getOrCreateToken(session);
        req.setAttribute("csrfToken", csrfToken);
        req.setAttribute("student", student);
        req.setAttribute("principal", principal);
        req.setAttribute("driveItems", items);
        req.setAttribute("pageTitle", "Eligible Recruitment Drives");
        req.setAttribute("activeNav", "drives");

        req.getRequestDispatcher(DRIVES_VIEW).forward(req, resp);
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

    private EligibilityService resolveEligibilityService() {
        if (this.eligibilityService != null) return this.eligibilityService;
        if (getServletContext().getAttribute(WebContextListener.ELIGIBILITY_SERVICE_ATTR) instanceof EligibilityService es) {
            this.eligibilityService = es;
            return es;
        }
        throw new IllegalStateException("EligibilityService is not available in ServletContext");
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
