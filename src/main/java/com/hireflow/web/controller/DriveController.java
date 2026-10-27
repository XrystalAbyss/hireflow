package com.hireflow.web.controller;

import com.hireflow.context.ApplicationContext;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.service.RecruitmentDriveService;
import com.hireflow.web.dto.PublicDriveDto;
import com.hireflow.web.listener.WebContextListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller managing the public recruitment drive directory (GET /drives).
 * <p>
 * Supports search, multi-criteria filtering, sorting, and bounded query responses.
 * Delegates data retrieval to {@link RecruitmentDriveService} and {@link CompanyRepository}.
 */
@WebServlet(name = "DriveController", urlPatterns = {"/drives"})
public class DriveController extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final String VIEW_DRIVES = "/WEB-INF/views/drives.jsp";
    private static final int MAX_RESULTS_LIMIT = 100;

    // Allowed sort keys to prevent injection or unexpected comparator behavior
    private static final Set<String> ALLOWED_SORTS = Set.of(
            "deadline-asc", "deadline-desc",
            "company-asc", "company-desc",
            "cgpa-asc", "cgpa-desc"
    );

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        RecruitmentDriveService driveService = resolveDriveService();
        CompanyRepository companyRepository = resolveCompanyRepository();

        if (driveService == null) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Recruitment drive service unavailable.");
            return;
        }

        // 1. Fetch raw domain drives from service
        List<RecruitmentDrive> domainDrives = driveService.getAllDrives();

        // 2. Project into safe public DTOs
        List<PublicDriveDto> allDtos = domainDrives.stream()
                .map(drive -> {
                    String companyName = drive.getCompanyId();
                    if (companyRepository != null) {
                        companyName = companyRepository.findById(drive.getCompanyId())
                                .map(Company::getName)
                                .orElse(drive.getCompanyId());
                    }
                    return PublicDriveDto.from(drive, companyName);
                })
                .collect(Collectors.toList());

        // Extract available branches across all drives for the filter dropdown
        Set<String> availableBranches = new TreeSet<>();
        for (PublicDriveDto dto : allDtos) {
            availableBranches.addAll(dto.getEligibleBranches());
        }

        // 3. Extract and sanitize filter/search parameters
        String query = sanitizeStringParam(req.getParameter("q"), 80);
        String branch = sanitizeStringParam(req.getParameter("branch"), 20);
        String status = sanitizeStringParam(req.getParameter("status"), 10);
        String sort = sanitizeSortParam(req.getParameter("sort"));

        // 4. Apply filtering
        List<PublicDriveDto> filtered = allDtos.stream()
                // Keyword search filter (matches job title or company name)
                .filter(dto -> {
                    if (query == null || query.isEmpty()) return true;
                    String qLower = query.toLowerCase();
                    return dto.getJobTitle().toLowerCase().contains(qLower)
                            || dto.getCompanyName().toLowerCase().contains(qLower)
                            || dto.getDriveId().toLowerCase().contains(qLower);
                })
                // Branch filter
                .filter(dto -> {
                    if (branch == null || branch.isEmpty() || "ALL".equalsIgnoreCase(branch)) return true;
                    return dto.getEligibleBranches().stream()
                            .anyMatch(b -> b.equalsIgnoreCase(branch) || "*".equals(b) || "ALL".equalsIgnoreCase(b));
                })
                // Status filter (active/open vs closed)
                .filter(dto -> {
                    if (status == null || status.isEmpty() || "all".equalsIgnoreCase(status)) return true;
                    if ("active".equalsIgnoreCase(status) || "open".equalsIgnoreCase(status)) return dto.isActive();
                    if ("closed".equalsIgnoreCase(status)) return !dto.isActive();
                    return true;
                })
                .collect(Collectors.toList());

        // 5. Apply sorting
        Comparator<PublicDriveDto> comparator;
        switch (sort) {
            case "deadline-desc":
                comparator = Comparator.comparing(PublicDriveDto::getApplicationDeadline).reversed();
                break;
            case "company-asc":
                comparator = Comparator.comparing(dto -> dto.getCompanyName().toLowerCase());
                break;
            case "company-desc":
                comparator = Comparator.comparing((PublicDriveDto dto) -> dto.getCompanyName().toLowerCase()).reversed();
                break;
            case "cgpa-asc":
                comparator = Comparator.comparingDouble(PublicDriveDto::getMinCgpa);
                break;
            case "cgpa-desc":
                comparator = Comparator.comparingDouble(PublicDriveDto::getMinCgpa).reversed();
                break;
            case "deadline-asc":
            default:
                // Default: Active first, then deadline ascending
                comparator = Comparator.comparing(PublicDriveDto::isActive).reversed()
                        .thenComparing(PublicDriveDto::getApplicationDeadline);
                break;
        }
        filtered.sort(comparator);

        // 6. Apply bounded result limit
        if (filtered.size() > MAX_RESULTS_LIMIT) {
            filtered = filtered.subList(0, MAX_RESULTS_LIMIT);
        }

        // 7. Calculate catalog metrics
        long totalActive = allDtos.stream().filter(PublicDriveDto::isActive).count();
        long totalCompanies = allDtos.stream().map(PublicDriveDto::getCompanyId).distinct().count();

        // 8. Bind request attributes for view rendering
        req.setAttribute("drives", filtered);
        req.setAttribute("totalDrives", allDtos.size());
        req.setAttribute("activeDrives", totalActive);
        req.setAttribute("totalCompanies", totalCompanies);
        req.setAttribute("filteredCount", filtered.size());
        req.setAttribute("availableBranches", availableBranches);

        // Sticky filter attributes for form repopulation
        req.setAttribute("paramQuery", query != null ? query : "");
        req.setAttribute("paramBranch", branch != null ? branch : "ALL");
        req.setAttribute("paramStatus", status != null ? status : "all");
        req.setAttribute("paramSort", sort);

        req.getRequestDispatcher(VIEW_DRIVES).forward(req, resp);
    }

    private String sanitizeStringParam(String value, int maxLength) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > maxLength) {
            trimmed = trimmed.substring(0, maxLength);
        }
        return trimmed;
    }

    private String sanitizeSortParam(String sort) {
        if (sort == null) return "deadline-asc";
        String trimmed = sort.trim().toLowerCase();
        return ALLOWED_SORTS.contains(trimmed) ? trimmed : "deadline-asc";
    }

    private RecruitmentDriveService resolveDriveService() {
        Object attr = getServletContext().getAttribute(WebContextListener.DRIVE_SERVICE_ATTR);
        if (attr instanceof RecruitmentDriveService) {
            return (RecruitmentDriveService) attr;
        }
        Object ctxAttr = getServletContext().getAttribute(WebContextListener.APP_CONTEXT_ATTR);
        if (ctxAttr instanceof ApplicationContext) {
            return ((ApplicationContext) ctxAttr).getDriveService();
        }
        return null;
    }

    private CompanyRepository resolveCompanyRepository() {
        Object attr = getServletContext().getAttribute(WebContextListener.COMPANY_REPO_ATTR);
        if (attr instanceof CompanyRepository) {
            return (CompanyRepository) attr;
        }
        Object ctxAttr = getServletContext().getAttribute(WebContextListener.APP_CONTEXT_ATTR);
        if (ctxAttr instanceof ApplicationContext) {
            return ((ApplicationContext) ctxAttr).getCompanyRepository();
        }
        return null;
    }
}
