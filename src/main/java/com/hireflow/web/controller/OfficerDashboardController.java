package com.hireflow.web.controller;

import com.hireflow.context.ApplicationContext;
import com.hireflow.model.ApplicationStatus;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.CompanyService;
import com.hireflow.service.RecruitmentDriveService;
import com.hireflow.web.dto.OfficerDashboardMetrics;
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
import java.util.Collections;
import java.util.List;

/**
 * Controller serving the authenticated Training & Placement Officer console (GET /officer/dashboard).
 * Displays real-time database-backed recruitment metrics, active drives, and quick management links.
 */
public class OfficerDashboardController extends HttpServlet {

    private static final String OFFICER_DASHBOARD_VIEW = "/WEB-INF/views/officer/dashboard.jsp";

    private CompanyRepository companyRepository;
    private RecruitmentDriveService driveService;
    private ApplicationService applicationService;

    public OfficerDashboardController() {
    }

    public OfficerDashboardController(CompanyRepository companyRepository,
                                      RecruitmentDriveService driveService,
                                      ApplicationService applicationService) {
        this.companyRepository = companyRepository;
        this.driveService = driveService;
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
                if (this.companyRepository == null) {
                    this.companyRepository = (CompanyRepository) sc.getAttribute(WebContextListener.COMPANY_REPO_ATTR);
                }
                if (this.driveService == null) {
                    this.driveService = (RecruitmentDriveService) sc.getAttribute(WebContextListener.DRIVE_SERVICE_ATTR);
                }
                if (this.applicationService == null) {
                    this.applicationService = (ApplicationService) sc.getAttribute(WebContextListener.APPLICATION_SERVICE_ATTR);
                }
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);

        HttpSession session = req.getSession(false);
        UserPrincipal principal = (session != null)
                ? (UserPrincipal) session.getAttribute(PortalAuthorizationFilter.SESSION_USER_ATTR)
                : null;

        if (principal == null || !principal.isPlacementOfficer()) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied to officer dashboard.");
            return;
        }

        resolveDependencies();
        if (companyRepository == null && req.getServletContext() != null) {
            companyRepository = (CompanyRepository) req.getServletContext().getAttribute(WebContextListener.COMPANY_REPO_ATTR);
        }
        if (driveService == null && req.getServletContext() != null) {
            driveService = (RecruitmentDriveService) req.getServletContext().getAttribute(WebContextListener.DRIVE_SERVICE_ATTR);
        }
        if (applicationService == null && req.getServletContext() != null) {
            applicationService = (ApplicationService) req.getServletContext().getAttribute(WebContextListener.APPLICATION_SERVICE_ATTR);
        }

        int totalCompanies = companyRepository != null ? companyRepository.count() : 0;
        int totalDrives = driveService != null ? driveService.getAllDrives().size() : 0;
        List<RecruitmentDrive> activeDrivesList = driveService != null ? driveService.getActiveDrives() : Collections.emptyList();
        int activeDrives = activeDrivesList.size();
        int totalApps = applicationService != null ? applicationService.getAllApplications().size() : 0;
        int shortlistedApps = applicationService != null ? applicationService.getApplicationsByStatus(ApplicationStatus.SHORTLISTED).size() : 0;
        int scheduledApps = applicationService != null ? applicationService.getApplicationsByStatus(ApplicationStatus.SCHEDULED).size() : 0;

        List<RecruitmentDrive> topDrives = activeDrivesList.stream().limit(5).toList();

        OfficerDashboardMetrics metrics = new OfficerDashboardMetrics(
                totalCompanies,
                totalDrives,
                activeDrives,
                totalApps,
                shortlistedApps,
                scheduledApps,
                topDrives
        );

        String csrfToken = CsrfTokenManager.getOrCreateToken(session);
        req.setAttribute("user", principal);
        req.setAttribute("csrfToken", csrfToken);
        req.setAttribute("metrics", metrics);

        req.getRequestDispatcher(OFFICER_DASHBOARD_VIEW).forward(req, resp);
    }
}
