package com.hireflow.web.controller;

import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.Application;
import com.hireflow.model.ApplicationStatus;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Student;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.CompanyService;
import com.hireflow.service.EligibilityService;
import com.hireflow.service.RecruitmentDriveService;
import com.hireflow.service.StudentService;
import com.hireflow.web.dto.OfficerApplicantViewItem;
import com.hireflow.web.filter.PortalAuthorizationFilter;
import com.hireflow.web.listener.WebContextListener;
import com.hireflow.web.security.CsrfTokenManager;
import com.hireflow.web.security.UserPrincipal;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Controller handling applicant reviews, eligibility evaluations, and candidate shortlisting:
 * <ul>
 *     <li>GET  /officer/applications           - List applications across drives with filters & pagination</li>
 *     <li>POST /officer/applications/{id}/shortlist - Shortlist an eligible applicant</li>
 * </ul>
 */
public class OfficerApplicationController extends HttpServlet {

    private static final String APPLICATIONS_VIEW = "/WEB-INF/views/officer/applications.jsp";
    private static final Pattern SHORTLIST_PATTERN = Pattern.compile("^/([^/]+)/shortlist/?$");

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;

    private ApplicationService applicationService;
    private RecruitmentDriveService driveService;
    private StudentService studentService;
    private CompanyService companyService;
    private EligibilityService eligibilityService;

    public OfficerApplicationController() {
    }

    public OfficerApplicationController(ApplicationService applicationService,
                                        RecruitmentDriveService driveService,
                                        StudentService studentService,
                                        CompanyService companyService,
                                        EligibilityService eligibilityService) {
        this.applicationService = applicationService;
        this.driveService = driveService;
        this.studentService = studentService;
        this.companyService = companyService;
        this.eligibilityService = eligibilityService;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        resolveDependencies();
    }

    private void resolveDependencies() {
        ServletConfig config = getServletConfig();
        if (config != null) {
            ServletContext sc = config.getServletContext();
            if (sc != null) {
                if (this.applicationService == null) {
                    this.applicationService = (ApplicationService) sc.getAttribute(WebContextListener.APPLICATION_SERVICE_ATTR);
                }
                if (this.driveService == null) {
                    this.driveService = (RecruitmentDriveService) sc.getAttribute(WebContextListener.DRIVE_SERVICE_ATTR);
                }
                if (this.studentService == null) {
                    this.studentService = (StudentService) sc.getAttribute(WebContextListener.STUDENT_SERVICE_ATTR);
                }
                if (this.companyService == null) {
                    this.companyService = (CompanyService) sc.getAttribute(WebContextListener.COMPANY_SERVICE_ATTR);
                }
                if (this.eligibilityService == null) {
                    this.eligibilityService = (EligibilityService) sc.getAttribute(WebContextListener.ELIGIBILITY_SERVICE_ATTR);
                }
            }
        }
    }

    private UserPrincipal verifyOfficer(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        UserPrincipal principal = (session != null)
                ? (UserPrincipal) session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)
                : null;
        if (principal == null || !principal.isPlacementOfficer()) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied: Officer account required.");
            return null;
        }
        return principal;
    }

    private void setAntiCacheHeaders(HttpServletResponse resp) {
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        setAntiCacheHeaders(resp);
        UserPrincipal principal = verifyOfficer(req, resp);
        if (principal == null) return;

        resolveDependencies();
        if (applicationService == null && req.getServletContext() != null) {
            applicationService = (ApplicationService) req.getServletContext().getAttribute(WebContextListener.APPLICATION_SERVICE_ATTR);
        }
        if (driveService == null && req.getServletContext() != null) {
            driveService = (RecruitmentDriveService) req.getServletContext().getAttribute(WebContextListener.DRIVE_SERVICE_ATTR);
        }
        if (studentService == null && req.getServletContext() != null) {
            studentService = (StudentService) req.getServletContext().getAttribute(WebContextListener.STUDENT_SERVICE_ATTR);
        }
        if (companyService == null && req.getServletContext() != null) {
            companyService = (CompanyService) req.getServletContext().getAttribute(WebContextListener.COMPANY_SERVICE_ATTR);
        }
        if (eligibilityService == null && req.getServletContext() != null) {
            eligibilityService = (EligibilityService) req.getServletContext().getAttribute(WebContextListener.ELIGIBILITY_SERVICE_ATTR);
        }

        HttpSession session = req.getSession(true);
        String csrfToken = CsrfTokenManager.getOrCreateToken(session);
        req.setAttribute("user", principal);
        req.setAttribute("csrfToken", csrfToken);

        // Filters: driveId, status, query
        String filterDriveId = req.getParameter("driveId");
        if (filterDriveId != null && filterDriveId.trim().isEmpty()) {
            filterDriveId = null;
        } else if (filterDriveId != null) {
            filterDriveId = filterDriveId.trim().toUpperCase();
        }

        String filterStatus = req.getParameter("status");
        if (filterStatus != null && filterStatus.trim().isEmpty()) {
            filterStatus = null;
        }

        String searchQuery = req.getParameter("q");
        if (searchQuery != null && searchQuery.trim().isEmpty()) {
            searchQuery = null;
        }

        // Bounded pagination
        int page = 1;
        try {
            String pageStr = req.getParameter("page");
            if (pageStr != null && !pageStr.trim().isEmpty()) {
                page = Math.max(1, Integer.parseInt(pageStr.trim()));
            }
        } catch (NumberFormatException ignored) {
        }

        int pageSize = DEFAULT_PAGE_SIZE;
        try {
            String sizeStr = req.getParameter("pageSize");
            if (sizeStr != null && !sizeStr.trim().isEmpty()) {
                pageSize = Math.min(MAX_PAGE_SIZE, Math.max(5, Integer.parseInt(sizeStr.trim())));
            }
        } catch (NumberFormatException ignored) {
        }

        // Batch lookups to eliminate N+1 queries
        List<Application> allApps = applicationService != null ? applicationService.getAllApplications() : List.of();
        Map<String, Student> studentMap = studentService != null
                ? studentService.getAllStudents().stream().collect(Collectors.toMap(Student::getId, s -> s, (a, b) -> a))
                : Map.of();
        Map<String, RecruitmentDrive> driveMap = driveService != null
                ? driveService.getAllDrives().stream().collect(Collectors.toMap(RecruitmentDrive::getId, d -> d, (a, b) -> a))
                : Map.of();
        Map<String, Company> companyMap = companyService != null
                ? companyService.getAllCompanies().stream().collect(Collectors.toMap(Company::getId, c -> c, (a, b) -> a))
                : Map.of();

        // Filter applications
        List<OfficerApplicantViewItem> items = new ArrayList<>();
        for (Application app : allApps) {
            if (filterDriveId != null && !app.getDriveId().equalsIgnoreCase(filterDriveId)) {
                continue;
            }
            if (filterStatus != null && !"ALL".equalsIgnoreCase(filterStatus)) {
                if (!app.getStatus().name().equalsIgnoreCase(filterStatus)) {
                    continue;
                }
            }

            Student student = studentMap.get(app.getStudentId());
            RecruitmentDrive drive = driveMap.get(app.getDriveId());
            Company company = drive != null ? companyMap.get(drive.getCompanyId()) : null;

            if (searchQuery != null) {
                String q = searchQuery.toLowerCase();
                boolean matchesStudent = student != null && (student.getName().toLowerCase().contains(q)
                        || student.getId().toLowerCase().contains(q)
                        || student.getBranch().toLowerCase().contains(q));
                boolean matchesJob = drive != null && drive.getJobTitle().toLowerCase().contains(q);
                boolean matchesCompany = company != null && company.getName().toLowerCase().contains(q);
                if (!matchesStudent && !matchesJob && !matchesCompany && !app.getId().toLowerCase().contains(q)) {
                    continue;
                }
            }

            EligibilityService.EligibilityResult eligibility = (eligibilityService != null && student != null && drive != null)
                    ? eligibilityService.checkEligibility(student, drive)
                    : null;

            items.add(new OfficerApplicantViewItem(app, student, drive, company, eligibility));
        }

        // Sort applications by appliedAt descending (newest first)
        items.sort(Comparator.comparing(OfficerApplicantViewItem::getAppliedAt).reversed());

        int totalCount = items.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / pageSize));
        if (page > totalPages) {
            page = totalPages;
        }

        int fromIndex = (page - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalCount);
        List<OfficerApplicantViewItem> pagedItems = (fromIndex < totalCount)
                ? items.subList(fromIndex, toIndex)
                : Collections.emptyList();

        List<RecruitmentDrive> drivesList = driveService != null ? driveService.getAllDrives() : List.of();

        req.setAttribute("applicants", pagedItems);
        req.setAttribute("totalCount", totalCount);
        req.setAttribute("currentPage", page);
        req.setAttribute("totalPages", totalPages);
        req.setAttribute("pageSize", pageSize);
        req.setAttribute("filterDriveId", filterDriveId);
        req.setAttribute("filterStatus", filterStatus);
        req.setAttribute("searchQuery", searchQuery);
        req.setAttribute("drivesList", drivesList);
        req.setAttribute("allStatuses", ApplicationStatus.values());

        req.getRequestDispatcher(APPLICATIONS_VIEW).forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        setAntiCacheHeaders(resp);
        UserPrincipal principal = verifyOfficer(req, resp);
        if (principal == null) return;

        resolveDependencies();
        if (applicationService == null && req.getServletContext() != null) {
            applicationService = (ApplicationService) req.getServletContext().getAttribute(WebContextListener.APPLICATION_SERVICE_ATTR);
        }

        HttpSession session = req.getSession(false);
        String submittedCsrf = req.getParameter("_csrf");
        if (submittedCsrf == null || session == null || !CsrfTokenManager.isValidToken(session, submittedCsrf)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF token.");
            return;
        }

        String pathInfo = req.getPathInfo();
        String action = req.getParameter("action");

        String appId = null;
        if (pathInfo != null) {
            Matcher m = SHORTLIST_PATTERN.matcher(pathInfo);
            if (m.matches()) {
                appId = m.group(1);
            }
        }
        if (appId == null && "shortlist".equalsIgnoreCase(action)) {
            appId = req.getParameter("applicationId");
        }

        String redirectTarget = req.getParameter("returnUrl");
        if (redirectTarget == null || redirectTarget.trim().isEmpty() || !redirectTarget.startsWith(req.getContextPath())) {
            redirectTarget = req.getContextPath() + "/officer/applications";
        }

        if (appId == null || appId.trim().isEmpty()) {
            session.setAttribute("flash_error", "Missing application ID for shortlist action.");
            resp.sendRedirect(redirectTarget);
            return;
        }

        try {
            if (applicationService == null) throw new IllegalStateException("Application service unavailable.");
            Application updated = applicationService.shortlistApplication(appId.trim());
            session.setAttribute("flash_success", "Application '" + updated.getId() + "' successfully shortlisted for interviews.");
        } catch (ValidationException | EntityNotFoundException ex) {
            session.setAttribute("flash_error", ex.getMessage());
        } catch (Exception ex) {
            session.setAttribute("flash_error", "Failed to shortlist application: " + ex.getMessage());
        }

        resp.sendRedirect(redirectTarget);
    }
}
