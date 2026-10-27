package com.hireflow.web.controller;

import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.Company;
import com.hireflow.model.Interviewer;
import com.hireflow.model.InterviewSession;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.repository.InterviewerRepository;
import com.hireflow.repository.InterviewSessionRepository;
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
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Controller providing placement officer interview resource management for Interviewers:
 * <ul>
 *     <li>GET  /officer/interviewers          - List interviewers with corporate partner affiliation</li>
 *     <li>GET  /officer/interviewers/new      - Render interviewer creation form</li>
 *     <li>POST /officer/interviewers          - Create interviewer</li>
 *     <li>GET  /officer/interviewers/{id}/edit - Render edit form</li>
 *     <li>POST /officer/interviewers/{id}/update - Update interviewer</li>
 *     <li>POST /officer/interviewers/{id}/delete - Delete interviewer (enforces referential integrity against confirmed sessions)</li>
 * </ul>
 */
public class OfficerInterviewerController extends HttpServlet {

    private static final String LIST_VIEW = "/WEB-INF/views/officer/interviewers.jsp";
    private static final String FORM_VIEW = "/WEB-INF/views/officer/interviewer-form.jsp";

    private static final Pattern EDIT_PATTERN = Pattern.compile("^/([^/]+)/edit/?$");
    private static final Pattern UPDATE_PATTERN = Pattern.compile("^/([^/]+)/update/?$");
    private static final Pattern DELETE_PATTERN = Pattern.compile("^/([^/]+)/delete/?$");

    public record InterviewerViewItem(
            Interviewer interviewer,
            String companyName,
            int confirmedSessionCount
    ) {
        public Interviewer getInterviewer() { return interviewer; }
        public String getCompanyName() { return companyName; }
        public int getConfirmedSessionCount() { return confirmedSessionCount; }
    }

    private InterviewerRepository interviewerRepository;
    private CompanyRepository companyRepository;
    private InterviewSessionRepository sessionRepository;

    public OfficerInterviewerController() {
    }

    public OfficerInterviewerController(InterviewerRepository interviewerRepository,
                                        CompanyRepository companyRepository,
                                        InterviewSessionRepository sessionRepository) {
        this.interviewerRepository = interviewerRepository;
        this.companyRepository = companyRepository;
        this.sessionRepository = sessionRepository;
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
                if (this.interviewerRepository == null) {
                    this.interviewerRepository = (InterviewerRepository) sc.getAttribute(WebContextListener.INTERVIEWER_REPO_ATTR);
                }
                if (this.companyRepository == null) {
                    this.companyRepository = (CompanyRepository) sc.getAttribute(WebContextListener.COMPANY_REPO_ATTR);
                }
                if (this.sessionRepository == null) {
                    this.sessionRepository = (InterviewSessionRepository) sc.getAttribute(WebContextListener.SESSION_REPO_ATTR);
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
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, max-age=0");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        setAntiCacheHeaders(resp);
        UserPrincipal officer = verifyOfficer(req, resp);
        if (officer == null) return;

        resolveDependencies();
        String pathInfo = req.getPathInfo();
        String csrfToken = CsrfTokenManager.getOrCreateToken(req.getSession(true));
        req.setAttribute("csrfToken", csrfToken);
        req.setAttribute("user", officer);

        // 1. Route: /officer/interviewers/new
        if ("/new".equalsIgnoreCase(pathInfo)) {
            List<Company> companies = companyRepository != null ? companyRepository.findAll() : List.of();
            req.setAttribute("companies", companies);
            req.setAttribute("isEdit", false);
            req.setAttribute("pageTitle", "Register Interviewer - HireFlow");
            req.setAttribute("activeNav", "officer-interviewers");
            req.getRequestDispatcher(FORM_VIEW).forward(req, resp);
            return;
        }

        // 2. Route: /officer/interviewers/{id}/edit
        if (pathInfo != null) {
            Matcher editMatcher = EDIT_PATTERN.matcher(pathInfo);
            if (editMatcher.matches()) {
                String id = editMatcher.group(1);
                Optional<Interviewer> opt = interviewerRepository.findById(id);
                if (opt.isEmpty()) {
                    req.getSession().setAttribute("flash_error", "Interviewer '" + id + "' not found.");
                    resp.sendRedirect(req.getContextPath() + "/officer/interviewers");
                    return;
                }
                List<Company> companies = companyRepository != null ? companyRepository.findAll() : List.of();
                req.setAttribute("interviewer", opt.get());
                req.setAttribute("companies", companies);
                req.setAttribute("isEdit", true);
                req.setAttribute("pageTitle", "Edit Interviewer " + id + " - HireFlow");
                req.setAttribute("activeNav", "officer-interviewers");
                req.getRequestDispatcher(FORM_VIEW).forward(req, resp);
                return;
            }
        }

        // 3. Route: /officer/interviewers (List)
        List<Interviewer> interviewers = interviewerRepository != null ? interviewerRepository.findAll() : List.of();
        Map<String, Company> companyMap = (companyRepository != null)
                ? companyRepository.findAll().stream().collect(Collectors.toMap(Company::getId, Function.identity(), (a, b) -> a))
                : Map.of();

        List<InterviewerViewItem> items = new ArrayList<>();
        for (Interviewer i : interviewers) {
            String cName = Optional.ofNullable(companyMap.get(i.getCompanyId()))
                    .map(Company::getName)
                    .orElse(i.getCompanyId());
            int sessionCount = (sessionRepository != null) ? sessionRepository.findByInterviewerId(i.getId()).size() : 0;
            items.add(new InterviewerViewItem(i, cName, sessionCount));
        }

        items.sort(Comparator.comparing((InterviewerViewItem it) -> it.interviewer().getName()));

        req.setAttribute("items", items);
        req.setAttribute("pageTitle", "Corporate Interviewers - HireFlow");
        req.setAttribute("activeNav", "officer-interviewers");
        req.getRequestDispatcher(LIST_VIEW).forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        setAntiCacheHeaders(resp);
        UserPrincipal officer = verifyOfficer(req, resp);
        if (officer == null) return;

        // CSRF verification
        if (!CsrfTokenManager.validateToken(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing CSRF token.");
            return;
        }

        resolveDependencies();
        HttpSession session = req.getSession();
        String pathInfo = req.getPathInfo();
        String action = req.getParameter("action");

        // 1. Delete action
        String deleteId = null;
        if (pathInfo != null) {
            Matcher delMatcher = DELETE_PATTERN.matcher(pathInfo);
            if (delMatcher.matches()) {
                deleteId = delMatcher.group(1);
            }
        }
        if (deleteId == null && "delete".equalsIgnoreCase(action)) {
            deleteId = req.getParameter("id");
        }

        if (deleteId != null) {
            String normDelId = deleteId.trim().toUpperCase();
            try {
                if (sessionRepository != null) {
                    List<InterviewSession> confirmed = sessionRepository.findByInterviewerId(normDelId);
                    if (!confirmed.isEmpty()) {
                        throw new ValidationException(String.format(
                                "Cannot delete interviewer '%s' because %d confirmed interview session(s) reference this interviewer.",
                                normDelId, confirmed.size()
                        ));
                    }
                }
                boolean deleted = interviewerRepository.deleteById(normDelId);
                if (deleted) {
                    session.setAttribute("flash_success", "Interviewer '" + normDelId + "' deleted successfully.");
                } else {
                    session.setAttribute("flash_error", "Interviewer '" + normDelId + "' not found.");
                }
            } catch (Exception ex) {
                session.setAttribute("flash_error", ex.getMessage());
            }
            resp.sendRedirect(req.getContextPath() + "/officer/interviewers");
            return;
        }

        // 2. Update action
        String updateId = null;
        if (pathInfo != null) {
            Matcher updateMatcher = UPDATE_PATTERN.matcher(pathInfo);
            if (updateMatcher.matches()) {
                updateId = updateMatcher.group(1);
            }
        }
        if (updateId == null && "update".equalsIgnoreCase(action)) {
            updateId = req.getParameter("id");
        }

        if (updateId != null) {
            String normUpdateId = updateId.trim().toUpperCase();
            String name = req.getParameter("name");
            String companyId = req.getParameter("companyId");

            try {
                Optional<Interviewer> opt = interviewerRepository.findById(normUpdateId);
                if (opt.isEmpty()) {
                    throw new EntityNotFoundException("Interviewer '" + normUpdateId + "' not found.");
                }
                if (companyRepository != null && !companyRepository.existsById(companyId != null ? companyId.trim().toUpperCase() : "")) {
                    throw new EntityNotFoundException("Company ID '" + companyId + "' does not exist.");
                }

                Interviewer interviewer = opt.get();
                interviewer.setName(name);
                interviewer.setCompanyId(companyId);
                interviewerRepository.update(interviewer);

                session.setAttribute("flash_success", "Interviewer '" + normUpdateId + "' updated successfully.");
                resp.sendRedirect(req.getContextPath() + "/officer/interviewers");
            } catch (Exception ex) {
                session.setAttribute("flash_error", ex.getMessage());
                resp.sendRedirect(req.getContextPath() + "/officer/interviewers/" + normUpdateId + "/edit");
            }
            return;
        }

        // 3. Create action
        String id = req.getParameter("id");
        String name = req.getParameter("name");
        String companyId = req.getParameter("companyId");

        try {
            if (id == null || id.trim().isEmpty()) {
                throw new ValidationException("Interviewer ID is required.");
            }
            String normId = id.trim().toUpperCase();
            if (interviewerRepository.existsById(normId)) {
                throw new DuplicateEntityException("Interviewer with ID '" + normId + "' already exists.");
            }
            if (companyRepository != null && !companyRepository.existsById(companyId != null ? companyId.trim().toUpperCase() : "")) {
                throw new EntityNotFoundException("Company ID '" + companyId + "' does not exist.");
            }

            Interviewer interviewer = new Interviewer(normId, name, companyId);
            interviewerRepository.add(interviewer);

            session.setAttribute("flash_success", "Interviewer '" + normId + "' created successfully.");
            resp.sendRedirect(req.getContextPath() + "/officer/interviewers");
        } catch (Exception ex) {
            session.setAttribute("flash_error", ex.getMessage());
            resp.sendRedirect(req.getContextPath() + "/officer/interviewers/new");
        }
    }
}
