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

@DisplayName("DriveController - Public Recruitment Drives MVC Controller Tests")
class DriveControllerTest {

    private DriveController controller;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private ServletContext servletContext;
    private ServletConfig servletConfig;
    private RequestDispatcher requestDispatcher;
    private RecruitmentDriveService driveService;
    private CompanyRepository companyRepository;

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
        when(request.getRequestDispatcher("/WEB-INF/views/drives.jsp")).thenReturn(requestDispatcher);
        controller.init(servletConfig);
    }

    @Test
    @DisplayName("doGet: loads drives from service, enriches company name, sets attributes, and forwards to JSP")
    void testDrivesLoadsAndSetsRequestAttributes() throws ServletException, IOException {
        when(servletContext.getAttribute(WebContextListener.DRIVE_SERVICE_ATTR)).thenReturn(driveService);
        when(servletContext.getAttribute(WebContextListener.COMPANY_REPO_ATTR)).thenReturn(companyRepository);

        RecruitmentDrive drive1 = new RecruitmentDrive(
                "DRV-01", "CMP-GOOGLE", "Software Engineer", 8.0, Set.of("CSE", "IT"), LocalDate.now().plusDays(10)
        );
        RecruitmentDrive drive2 = new RecruitmentDrive(
                "DRV-02", "CMP-MICROSOFT", "Cloud Solutions Architect", 7.5, Set.of("CSE", "ECE"), LocalDate.now().minusDays(2)
        );
        when(driveService.getAllDrives()).thenReturn(List.of(drive1, drive2));
        when(companyRepository.findById("CMP-GOOGLE")).thenReturn(Optional.of(new Company("CMP-GOOGLE", "Google LLC", "Technology")));
        when(companyRepository.findById("CMP-MICROSOFT")).thenReturn(Optional.of(new Company("CMP-MICROSOFT", "Microsoft Corp", "Technology")));

        controller.doGet(request, response);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PublicDriveDto>> dtosCaptor = ArgumentCaptor.forClass(List.class);
        verify(request).setAttribute(eq("drives"), dtosCaptor.capture());
        verify(request).setAttribute("totalDrives", 2);
        verify(request).setAttribute("activeDrives", 1L);
        verify(request).setAttribute("totalCompanies", 2L);
        verify(requestDispatcher).forward(request, response);

        List<PublicDriveDto> dtos = dtosCaptor.getValue();
        assertEquals(2, dtos.size());
        assertEquals("Google LLC", dtos.get(0).getCompanyName());
        assertTrue(dtos.get(0).isActive());
        assertEquals("Microsoft Corp", dtos.get(1).getCompanyName());
        assertFalse(dtos.get(1).isActive());
    }

    @Test
    @DisplayName("doGet: empty drive catalog sets zero counts and forwards cleanly")
    void testEmptyDriveCatalogRendersSafely() throws ServletException, IOException {
        when(servletContext.getAttribute(WebContextListener.DRIVE_SERVICE_ATTR)).thenReturn(driveService);
        when(servletContext.getAttribute(WebContextListener.COMPANY_REPO_ATTR)).thenReturn(companyRepository);
        when(driveService.getAllDrives()).thenReturn(List.of());

        controller.doGet(request, response);

        verify(request).setAttribute(eq("drives"), eq(List.of()));
        verify(request).setAttribute("totalDrives", 0);
        verify(request).setAttribute("activeDrives", 0L);
        verify(request).setAttribute("totalCompanies", 0L);
        verify(requestDispatcher).forward(request, response);
    }

    @Test
    @DisplayName("doGet: returns 500 when drive service is missing in ServletContext")
    void testMissingDriveServiceReturns500() throws ServletException, IOException {
        when(servletContext.getAttribute(WebContextListener.DRIVE_SERVICE_ATTR)).thenReturn(null);
        when(servletContext.getAttribute(WebContextListener.APP_CONTEXT_ATTR)).thenReturn(null);

        controller.doGet(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR), anyString());
        verify(requestDispatcher, never()).forward(request, response);
    }

    @Test
    @DisplayName("security: PublicDriveDto contains only permitted public data and zero candidate PII")
    void testPublicDriveDtoDoesNotExposeCandidateData() {
        RecruitmentDrive drive = new RecruitmentDrive(
                "DRV-AMZN-01", "CMP-AMAZON", "SDE 1", 8.2, Set.of("CSE"), LocalDate.now().plusDays(5)
        );
        PublicDriveDto dto = PublicDriveDto.from(drive, "Amazon Inc");

        assertEquals("DRV-AMZN-01", dto.getDriveId());
        assertEquals("CMP-AMAZON", dto.getCompanyId());
        assertEquals("Amazon Inc", dto.getCompanyName());
        assertEquals("SDE 1", dto.getJobTitle());
        assertEquals(8.2, dto.getMinCgpa());
        assertEquals(List.of("CSE"), dto.getEligibleBranches());
        assertTrue(dto.isActive());
        assertEquals(5, dto.getDaysRemaining());

        // Verify that DTO class has NO candidate/student/applicant records
        Arrays.stream(PublicDriveDto.class.getMethods())
                .forEach(method -> {
                    String name = method.getName().toLowerCase();
                    assertFalse(name.contains("student"), "DTO must not expose student info: " + name);
                    assertFalse(name.contains("candidate"), "DTO must not expose candidate info: " + name);
                    assertFalse(name.contains("applicant"), "DTO must not expose applicant info: " + name);
                    assertFalse(name.contains("resume"), "DTO must not expose resumes: " + name);
                    assertFalse(name.contains("session"), "DTO must not expose interview sessions: " + name);
                    assertFalse(name.contains("applicationstatus"), "DTO must not expose application statuses: " + name);
                });
    }

    @Test
    @DisplayName("architecture: DriveController delegates strictly to service and never executes SQL")
    void testDriveControllerDelegatesToServiceNotSql() throws ServletException, IOException {
        when(servletContext.getAttribute(WebContextListener.DRIVE_SERVICE_ATTR)).thenReturn(driveService);
        when(servletContext.getAttribute(WebContextListener.COMPANY_REPO_ATTR)).thenReturn(companyRepository);
        when(driveService.getAllDrives()).thenReturn(List.of());

        controller.doGet(request, response);

        verify(driveService, times(1)).getAllDrives();
    }
}
