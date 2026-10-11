package com.hireflow.web.controller;

import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.Company;
import com.hireflow.service.CompanyService;
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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controller handling company management operations for placement officers:
 * <ul>
 *     <li>GET  /officer/companies          - List registered corporate partners</li>
 *     <li>GET  /officer/companies/new      - Render company creation form</li>
 *     <li>POST /officer/companies          - Create a new corporate partner</li>
 *     <li>GET  /officer/companies/{id}/edit - Render company edit form</li>
 *     <li>POST /officer/companies/{id}/update - Update corporate partner fields</li>
 * </ul>
 */
public class OfficerCompanyController extends HttpServlet {

    private static final String COMPANIES_LIST_VIEW = "/WEB-INF/views/officer/companies.jsp";
    private static final String COMPANY_FORM_VIEW = "/WEB-INF/views/officer/company-form.jsp";

    private static final Pattern EDIT_PATTERN = Pattern.compile("^/([^/]+)/edit/?$");
    private static final Pattern UPDATE_PATTERN = Pattern.compile("^/([^/]+)/update/?$");

    private CompanyService companyService;

    public OfficerCompanyController() {
    }

    public OfficerCompanyController(CompanyService companyService) {
        this.companyService = companyService;
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
            if (sc != null && this.companyService == null) {
                this.companyService = (CompanyService) sc.getAttribute(WebContextListener.COMPANY_SERVICE_ATTR);
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
        if (companyService == null && req.getServletContext() != null) {
            companyService = (CompanyService) req.getServletContext().getAttribute(WebContextListener.COMPANY_SERVICE_ATTR);
        }

        HttpSession session = req.getSession(true);
        String csrfToken = CsrfTokenManager.getOrCreateToken(session);
        req.setAttribute("user", principal);
        req.setAttribute("csrfToken", csrfToken);

        String pathInfo = req.getPathInfo();
        String action = req.getParameter("action");

        // 1. Render new company form
        if ("/new".equals(pathInfo) || "new".equalsIgnoreCase(action)) {
            req.setAttribute("formMode", "create");
            req.getRequestDispatcher(COMPANY_FORM_VIEW).forward(req, resp);
            return;
        }

        // 2. Render edit company form
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
            var optComp = companyService != null ? companyService.getCompanyById(editId) : java.util.Optional.<Company>empty();
            if (optComp.isEmpty()) {
                session.setAttribute("flash_error", "Company with ID '" + editId + "' was not found.");
                resp.sendRedirect(req.getContextPath() + "/officer/companies");
                return;
            }
            req.setAttribute("company", optComp.get());
            req.setAttribute("formMode", "edit");
            req.getRequestDispatcher(COMPANY_FORM_VIEW).forward(req, resp);
            return;
        }

        // 3. Default: List all companies
        List<Company> companies = companyService != null ? companyService.getAllCompanies() : List.of();
        req.setAttribute("companies", companies);
        req.getRequestDispatcher(COMPANIES_LIST_VIEW).forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        setAntiCacheHeaders(resp);
        UserPrincipal principal = verifyOfficer(req, resp);
        if (principal == null) return;

        resolveDependencies();
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

        // Determine if update or create
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
            // Update company
            String name = req.getParameter("name");
            String industry = req.getParameter("industry");

            try {
                if (companyService == null) {
                    throw new IllegalStateException("Company service unavailable.");
                }
                Company updated = companyService.updateCompany(updateId, name, industry);
                session.setAttribute("flash_success", "Company '" + updated.getName() + "' updated successfully.");
                resp.sendRedirect(req.getContextPath() + "/officer/companies");
            } catch (ValidationException | DuplicateEntityException | EntityNotFoundException ex) {
                session.setAttribute("flash_error", ex.getMessage());
                resp.sendRedirect(req.getContextPath() + "/officer/companies/" + updateId + "/edit");
            } catch (Exception ex) {
                session.setAttribute("flash_error", "Failed to update company: " + ex.getMessage());
                resp.sendRedirect(req.getContextPath() + "/officer/companies");
            }
            return;
        }

        // Create company
        String id = req.getParameter("id");
        String name = req.getParameter("name");
        String industry = req.getParameter("industry");

        try {
            if (companyService == null) {
                throw new IllegalStateException("Company service unavailable.");
            }
            Company created = companyService.registerCompany(id, name, industry);
            session.setAttribute("flash_success", "Company '" + created.getName() + "' registered successfully.");
            resp.sendRedirect(req.getContextPath() + "/officer/companies");
        } catch (ValidationException | DuplicateEntityException ex) {
            session.setAttribute("flash_error", ex.getMessage());
            resp.sendRedirect(req.getContextPath() + "/officer/companies/new");
        } catch (Exception ex) {
            session.setAttribute("flash_error", "Failed to register company: " + ex.getMessage());
            resp.sendRedirect(req.getContextPath() + "/officer/companies/new");
        }
    }
}
