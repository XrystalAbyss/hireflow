package com.hireflow.web.controller;

import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.service.RecruitmentDriveService;
import com.hireflow.web.dto.PublicDriveDto;
import com.hireflow.web.listener.WebContextListener;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("DriveFilterAndSortTest - Public Drives Search, Filter, Sort and Bounds Tests")
class DriveFilterAndSortTest {

    private DriveController controller;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private ServletContext servletContext;
    private ServletConfig servletConfig;
    private RequestDispatcher requestDispatcher;
    private RecruitmentDriveService driveService;
    private CompanyRepository companyRepository;

    private List<RecruitmentDrive> sampleDrives;

    @BeforeEach
    void setUp() throws Exception {
        controller = new DriveController();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        servletContext = mock(ServletContext.class);
        servletConfig = mock(ServletConfig.class);
        requestDispatcher = mock(RequestDispatcher.class);
        driveService = mock(RecruitmentDriveService.class);
        companyRepository = mock(CompanyRepository.class);

        when(servletConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(WebContextListener.DRIVE_SERVICE_ATTR)).thenReturn(driveService);
        when(servletContext.getAttribute(WebContextListener.COMPANY_REPO_ATTR)).thenReturn(companyRepository);
        when(request.getRequestDispatcher("/WEB-INF/views/drives.jsp")).thenReturn(requestDispatcher);
        controller.init(servletConfig);

        sampleDrives = List.of(
                new RecruitmentDrive("DRV-01", "CMP-GOOGLE", "Software Development Engineer", 8.0, Set.of("CSE", "IT"), LocalDate.now().plusDays(10)),
                new RecruitmentDrive("DRV-02", "CMP-MICROSOFT", "Cloud Solutions Architect", 7.5, Set.of("CSE", "ECE"), LocalDate.now().plusDays(5)),
                new RecruitmentDrive("DRV-03", "CMP-GOLDMAN", "Quantitative Risk Analyst", 8.5, Set.of("CSE", "IT", "ECE"), LocalDate.now().plusDays(20)),
                new RecruitmentDrive("DRV-04", "CMP-TATA", "Automotive Design Engineer", 7.0, Set.of("MECH", "CIVIL"), LocalDate.now().minusDays(5)) // Closed
        );

        when(driveService.getAllDrives()).thenReturn(sampleDrives);
        when(companyRepository.findById("CMP-GOOGLE")).thenReturn(Optional.of(new Company("CMP-GOOGLE", "Google LLC", "Technology")));
        when(companyRepository.findById("CMP-MICROSOFT")).thenReturn(Optional.of(new Company("CMP-MICROSOFT", "Microsoft Corp", "Technology")));
        when(companyRepository.findById("CMP-GOLDMAN")).thenReturn(Optional.of(new Company("CMP-GOLDMAN", "Goldman Sachs", "Finance")));
        when(companyRepository.findById("CMP-TATA")).thenReturn(Optional.of(new Company("CMP-TATA", "Tata Motors", "Automotive")));
    }

    @Test
    @DisplayName("filtering: keyword query matches job title or company name (case-insensitive)")
    void testFilterByKeywordSearch() throws ServletException, IOException {
        when(request.getParameter("q")).thenReturn("software");

        controller.doGet(request, response);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PublicDriveDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(request).setAttribute(eq("drives"), captor.capture());

        List<PublicDriveDto> results = captor.getValue();
        assertEquals(1, results.size());
        assertEquals("DRV-01", results.get(0).getDriveId());
    }

    @Test
    @DisplayName("filtering: branch filter isolates drives permitting that academic branch")
    void testFilterByBranch() throws ServletException, IOException {
        when(request.getParameter("branch")).thenReturn("MECH");

        controller.doGet(request, response);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PublicDriveDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(request).setAttribute(eq("drives"), captor.capture());

        List<PublicDriveDto> results = captor.getValue();
        assertEquals(1, results.size());
        assertEquals("DRV-04", results.get(0).getDriveId());
    }

    @Test
    @DisplayName("filtering: status filter separates active/open from closed drives")
    void testFilterByStatusActiveAndClosed() throws ServletException, IOException {
        // Active filter
        when(request.getParameter("status")).thenReturn("active");
        controller.doGet(request, response);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PublicDriveDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(request).setAttribute(eq("drives"), captor.capture());

        List<PublicDriveDto> activeResults = captor.getValue();
        assertEquals(3, activeResults.size(), "Should return only 3 open drives");
        assertTrue(activeResults.stream().allMatch(PublicDriveDto::isActive));
    }

    @Test
    @DisplayName("sorting: company-asc sorts companies alphabetically A -> Z")
    void testSortByCompanyAsc() throws ServletException, IOException {
        when(request.getParameter("sort")).thenReturn("company-asc");

        controller.doGet(request, response);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PublicDriveDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(request).setAttribute(eq("drives"), captor.capture());

        List<PublicDriveDto> sorted = captor.getValue();
        assertEquals(4, sorted.size());
        assertEquals("Goldman Sachs", sorted.get(0).getCompanyName());
        assertEquals("Google LLC", sorted.get(1).getCompanyName());
        assertEquals("Microsoft Corp", sorted.get(2).getCompanyName());
        assertEquals("Tata Motors", sorted.get(3).getCompanyName());
    }

    @Test
    @DisplayName("safety: invalid sort parameter falls back gracefully to default deadline-asc")
    void testInvalidSortFallsBackToDefault() throws ServletException, IOException {
        when(request.getParameter("sort")).thenReturn("MALICIOUS_INJECTION_KEY");

        controller.doGet(request, response);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PublicDriveDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(request).setAttribute(eq("drives"), captor.capture());

        List<PublicDriveDto> sorted = captor.getValue();
        assertEquals(4, sorted.size());
        // Default sorts active first, then earliest deadline
        assertTrue(sorted.get(0).isActive());
        assertEquals("deadline-asc", sorted.get(0).getDaysRemaining() <= sorted.get(1).getDaysRemaining() ? "deadline-asc" : "other");
    }

    @Test
    @DisplayName("empty-state: query matching zero drives returns empty list with filteredCount=0")
    void testEmptySearchResultsRenderEmptyState() throws ServletException, IOException {
        when(request.getParameter("q")).thenReturn("NonexistentUnicornStartupXYZ");

        controller.doGet(request, response);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PublicDriveDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(request).setAttribute(eq("drives"), captor.capture());
        verify(request).setAttribute("filteredCount", 0);

        List<PublicDriveDto> results = captor.getValue();
        assertTrue(results.isEmpty());
    }
}
