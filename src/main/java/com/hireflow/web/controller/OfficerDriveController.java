package com.hireflow.web.controller;

import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.CompanyService;
import com.hireflow.service.RecruitmentDriveService;
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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Controller handling recruitment drive management for placement officers:
 * <ul>
 *     <li>GET  /officer/drives              - List all recruitment drives</li>
 *     <li>GET  /officer/drives/new          - Render drive creation form</li>
 *     <li>POST /officer/drives              - Create a new recruitment drive</li>
 *     <li>GET  /officer/drives/{id}         - View drive details and application status</li>
 *     <li>GET  /officer/drives/{id}/edit    - Render drive edit form</li>
 *     <li>POST /officer/drives/{id}/update  - Update recruitment drive fields</li>
 *     <li>POST /officer/drives/{id}/close   - Close recruitment drive to stop applications</li>
 * </ul>
 */
public class OfficerDriveController extends HttpServlet {

    private static final String DRIVES_LIST_VIEW = "/WEB-INF/views/officer/drives.jsp";
    private static final String DRIVE_FORM_VIEW = "/WEB-INF/views/officer/drive-form.jsp";
    private static final String DRIVE_DETAIL_VIEW = "/WEB-INF/views/officer/drive-detail.jsp";

    private static final Pattern EDIT_PATTERN = Pattern.compile("^/([^/]+)/edit/?$");
    private static final Pattern UPDATE_PATTERN = Pattern.compile("^/([^/]+)/update/?$");
    private static final Pattern CLOSE_PATTERN = Pattern.compile("^/([^/]+)/close/?$");
    private static final Pattern APPLICATIONS_PATTERN = Pattern.compile("^/([^/]+)/applications/?$");
    private static final Pattern DETAIL_PATTERN = Pattern.compile("^/([^/]+)/?$");

    private RecruitmentDriveService driveService;
    private CompanyService companyService;
    private ApplicationService applicationService;

    public OfficerDriveController() {
    }

    public OfficerDriveController(RecruitmentDriveService driveService,
                                  CompanyService companyService,
                                  ApplicationService applicationService) {
        this.driveService = driveService;
        this.companyService = companyService;
        this.applicationService = applicationService;
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
                if (this.driveService == null) {
                    this.driveService = (RecruitmentDriveService) sc.getAttribute(WebContextListener.DRIVE_SERVICE_ATTR);
                }
                if (this.companyService == null) {
                    this.companyService = (CompanyService) sc.getAttribute(WebContextListener.COMPANY_SERVICE_ATTR);
                }
                if (this.applicationService == null) {
                    this.applicationService = (ApplicationService) sc.getAttribute(WebContextListener.APPLICATION_SERVICE_ATTR);
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
        if (driveService == null && req.getServletContext() != null) {
            driveService = (RecruitmentDriveService) req.getServletContext().getAttribute(WebContextListener.DRIVE_SERVICE_ATTR);
        }
        if (companyService == null && req.getServletContext() != null) {
            companyService = (CompanyService) req.getServletContext().getAttribute(WebContextListener.COMPANY_SERVICE_ATTR);
        }
        if (applicationService == null && req.getServletContext() != null) {
            applicationService = (ApplicationService) req.getServletContext().getAttribute(WebContextListener.APPLICATION_SERVICE_ATTR);
        }

        HttpSession session = req.getSession(true);
        String csrfToken = CsrfTokenManager.getOrCreateToken(session);
        req.setAttribute("user", principal);
        req.setAttribute("csrfToken", csrfToken);

        String pathInfo = req.getPathInfo();
        String action = req.getParameter("action");

        // Forward /officer/drives/{id}/applications to application controller
        if (pathInfo != null) {
            Matcher appMatcher = APPLICATIONS_PATTERN.matcher(pathInfo);
            if (appMatcher.matches()) {
                String driveId = appMatcher.group(1);
                req.getRequestDispatcher("/officer/applications?driveId=" + driveId).forward(req, resp);
                return;
            }
        }

        // 1. Render new drive form
        if ("/new".equals(pathInfo) || "new".equalsIgnoreCase(action)) {
            List<Company> companies = companyService != null ? companyService.getAllCompanies() : List.of();
            req.setAttribute("companies", companies);
            req.setAttribute("formMode", "create");
            req.getRequestDispatcher(DRIVE_FORM_VIEW).forward(req, resp);
            return;
        }

        // 2. Render edit drive form
        String editId = null;
        if (pathInfo != null) {
            Matcher m = EDIT_PATTERN.matcher(pathInfo);
            if (m.matches()) {
                editId = m.group(1);
            }
        }
        if (editId == null && "edit".equalsIgnoreCase(action)) {
            editId = req.getParameter("id");
        }

        if (editId != null) {
            var optDrive = driveService != null ? driveService.getDriveById(editId) : Optional.<RecruitmentDrive>empty();
            if (optDrive.isEmpty()) {
                session.setAttribute("flash_error", "Recruitment Drive with ID '" + editId + "' was not found.");
                resp.sendRedirect(req.getContextPath() + "/officer/drives");
                return;
            }
            List<Company> companies = companyService != null ? companyService.getAllCompanies() : List.of();
            req.setAttribute("drive", optDrive.get());
            req.setAttribute("companies", companies);
            req.setAttribute("formMode", "edit");
            req.getRequestDispatcher(DRIVE_FORM_VIEW).forward(req, resp);
            return;
        }

        // 3. Render drive details
        String detailId = null;
        if (pathInfo != null) {
            Matcher m = DETAIL_PATTERN.matcher(pathInfo);
            if (m.matches() && !"new".equalsIgnoreCase(m.group(1))) {
                detailId = m.group(1);
            }
        }
        if (detailId == null && "detail".equalsIgnoreCase(action)) {
            detailId = req.getParameter("id");
        }

        if (detailId != null) {
            var optDrive = driveService != null ? driveService.getDriveById(detailId) : Optional.<RecruitmentDrive>empty();
            if (optDrive.isEmpty()) {
                session.setAttribute("flash_error", "Recruitment Drive with ID '" + detailId + "' was not found.");
                resp.sendRedirect(req.getContextPath() + "/officer/drives");
                return;
            }
            RecruitmentDrive drive = optDrive.get();
            Company company = companyService != null
                    ? companyService.getCompanyById(drive.getCompanyId()).orElse(null)
                    : null;
            int appCount = applicationService != null
                    ? applicationService.getApplicationsForDrive(drive.getId()).size()
                    : 0;

            req.setAttribute("drive", drive);
            req.setAttribute("company", company);
            req.setAttribute("applicationCount", appCount);
            req.getRequestDispatcher(DRIVE_DETAIL_VIEW).forward(req, resp);
            return;
        }

        // 4. Default: List all recruitment drives with companies map
        List<RecruitmentDrive> drives = driveService != null ? driveService.getAllDrives() : List.of();
        Map<String, String> companyNames = new HashMap<>();
        if (companyService != null) {
            for (Company c : companyService.getAllCompanies()) {
                companyNames.put(c.getId(), c.getName());
            }
        }

        req.setAttribute("drives", drives);
        req.setAttribute("companyNames", companyNames);
        req.getRequestDispatcher(DRIVES_LIST_VIEW).forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        setAntiCacheHeaders(resp);
        UserPrincipal principal = verifyOfficer(req, resp);
        if (principal == null) return;

        resolveDependencies();
        if (driveService == null && req.getServletContext() != null) {
            driveService = (RecruitmentDriveService) req.getServletContext().getAttribute(WebContextListener.DRIVE_SERVICE_ATTR);
        }
        if (companyService == null && req.getServletContext() != null) {
            companyService = (CompanyService) req.getServletContext().getAttribute(WebContextListener.COMPANY_SERVICE_ATTR);
        }

        HttpSession session = req.getSession(false);
        String submittedCsrf = req.getParameter("_csrf");
        if (submittedCsrf == null || session == null || !CsrfTokenManager.isValidToken(session, submittedCsrf)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF token.");
            return;
        }

        String pathInfo = req.getPathInfo();
        String action = req.getParameter("action");

        // 1. Close drive action
        String closeId = null;
        if (pathInfo != null) {
            Matcher m = CLOSE_PATTERN.matcher(pathInfo);
            if (m.matches()) {
                closeId = m.group(1);
            }
        }
        if (closeId == null && "close".equalsIgnoreCase(action)) {
            closeId = req.getParameter("id");
        }

        if (closeId != null) {
            try {
                if (driveService == null) throw new IllegalStateException("Drive service unavailable.");
                driveService.closeDrive(closeId);
                session.setAttribute("flash_success", "Recruitment Drive '" + closeId + "' has been closed successfully. No further student applications will be accepted.");
            } catch (Exception ex) {
                session.setAttribute("flash_error", "Failed to close drive: " + ex.getMessage());
            }
            resp.sendRedirect(req.getContextPath() + "/officer/drives");
            return;
        }

        // 2. Update drive action
        String updateId = null;
        if (pathInfo != null) {
            Matcher m = UPDATE_PATTERN.matcher(pathInfo);
            if (m.matches()) {
                updateId = m.group(1);
            }
        }
        if (updateId == null && "update".equalsIgnoreCase(action)) {
            updateId = req.getParameter("id");
        }

        if (updateId != null) {
            try {
                if (driveService == null) throw new IllegalStateException("Drive service unavailable.");
                RecruitmentDrive existing = driveService.getDriveOrThrow(updateId);

                String companyId = req.getParameter("companyId");
                String jobTitle = req.getParameter("jobTitle");
                String minCgpaStr = req.getParameter("minimumCgpa");
                String branchesStr = req.getParameter("eligibleBranches");
                String deadlineStr = req.getParameter("applicationDeadline");

                double minCgpa = Double.parseDouble(minCgpaStr != null ? minCgpaStr.trim() : "0.0");
                LocalDate deadline = LocalDate.parse(deadlineStr != null ? deadlineStr.trim() : "");
                Set<String> branches = parseBranches(branchesStr);

                existing.setCompanyId(companyId);
                existing.setJobTitle(jobTitle);
                existing.setMinimumCgpa(minCgpa);
                existing.setEligibleBranches(branches);
                existing.setApplicationDeadline(deadline);

                driveService.updateDrive(existing);
                session.setAttribute("flash_success", "Recruitment Drive '" + updateId + "' updated successfully.");
                resp.sendRedirect(req.getContextPath() + "/officer/drives");
            } catch (NumberFormatException ex) {
                session.setAttribute("flash_error", "Minimum CGPA must be a valid number (e.g. 7.50).");
                resp.sendRedirect(req.getContextPath() + "/officer/drives/" + updateId + "/edit");
            } catch (DateTimeParseException ex) {
                session.setAttribute("flash_error", "Deadline must follow format YYYY-MM-DD (e.g. 2026-11-15).");
                resp.sendRedirect(req.getContextPath() + "/officer/drives/" + updateId + "/edit");
            } catch (Exception ex) {
                session.setAttribute("flash_error", ex.getMessage());
                resp.sendRedirect(req.getContextPath() + "/officer/drives/" + updateId + "/edit");
            }
            return;
        }

        // 3. Create drive action
        String id = req.getParameter("id");
        String companyId = req.getParameter("companyId");
        String jobTitle = req.getParameter("jobTitle");
        String minCgpaStr = req.getParameter("minimumCgpa");
        String branchesStr = req.getParameter("eligibleBranches");
        String deadlineStr = req.getParameter("applicationDeadline");

        try {
            if (driveService == null) throw new IllegalStateException("Drive service unavailable.");
            if (id == null || id.trim().isEmpty()) {
                throw new ValidationException("Recruitment Drive ID is required.");
            }
            if (deadlineStr == null || deadlineStr.trim().isEmpty()) {
                throw new ValidationException("Application deadline is required.");
            }

            double minCgpa;
            try {
                minCgpa = Double.parseDouble(minCgpaStr != null ? minCgpaStr.trim() : "0.0");
            } catch (NumberFormatException nfe) {
                throw new ValidationException("Minimum CGPA must be a valid number (e.g. 7.50).");
            }

            LocalDate deadline;
            try {
                deadline = LocalDate.parse(deadlineStr.trim());
            } catch (DateTimeParseException dtpe) {
                throw new ValidationException("Deadline must follow format YYYY-MM-DD (e.g. 2026-11-15).");
            }

            if (deadline.isBefore(LocalDate.now())) {
                throw new ValidationException("Application deadline cannot be in the past.");
            }

            Set<String> branches = parseBranches(branchesStr);
            if (branches.isEmpty()) {
                throw new ValidationException("At least one eligible branch must be specified.");
            }

            RecruitmentDrive drive = driveService.createDrive(id, companyId, jobTitle, minCgpa, branches, deadline);
            session.setAttribute("flash_success", "Recruitment Drive '" + drive.getId() + "' created and published successfully.");
            resp.sendRedirect(req.getContextPath() + "/officer/drives");
        } catch (ValidationException | DuplicateEntityException | EntityNotFoundException ex) {
            session.setAttribute("flash_error", ex.getMessage());
            resp.sendRedirect(req.getContextPath() + "/officer/drives/new");
        } catch (Exception ex) {
            session.setAttribute("flash_error", "Failed to create recruitment drive: " + ex.getMessage());
            resp.sendRedirect(req.getContextPath() + "/officer/drives/new");
        }
    }

    private Set<String> parseBranches(String branchesStr) {
        if (branchesStr == null || branchesStr.trim().isEmpty()) {
            return Collections.emptySet();
        }
        return Arrays.stream(branchesStr.split("[,;\\s]+"))
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(b -> !b.isEmpty())
                .collect(Collectors.toSet());
    }
}
