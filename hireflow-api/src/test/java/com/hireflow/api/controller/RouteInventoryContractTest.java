package com.hireflow.api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("RouteInventoryContractTest - Authoritative Spring MVC Route Reconciliation")
class RouteInventoryContractTest {

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Verify exact count and mapping inventory of registered application REST endpoints")
    void verifyAuthoritativeRouteInventory() {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();

        List<String> appRoutes = new ArrayList<>();
        List<String> officerRoutes = new ArrayList<>();
        List<String> studentRoutes = new ArrayList<>();
        List<String> authRoutes = new ArrayList<>();
        List<String> publicRoutes = new ArrayList<>();
        List<String> healthRoutes = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod method = entry.getValue();

            // Filter only application controllers in com.hireflow.api.controller package
            String beanType = method.getBeanType().getName();
            if (!beanType.startsWith("com.hireflow.api.controller")) {
                continue;
            }

            Set<String> patterns = info.getDirectPaths();
            if (patterns.isEmpty() && info.getPathPatternsCondition() != null) {
                patterns = info.getPathPatternsCondition().getPatternValues();
            }

            var httpMethods = info.getMethodsCondition().getMethods();
            for (String pattern : patterns) {
                for (var httpMethod : httpMethods) {
                    String routeKey = httpMethod.name() + " " + pattern;
                    appRoutes.add(routeKey);

                    if (pattern.startsWith("/api/v1/officer")) {
                        officerRoutes.add(routeKey);
                    } else if (pattern.startsWith("/api/v1/student")) {
                        studentRoutes.add(routeKey);
                    } else if (pattern.startsWith("/api/v1/auth")) {
                        authRoutes.add(routeKey);
                    } else if (pattern.equals("/api/v1/health")) {
                        healthRoutes.add(routeKey);
                    } else if (pattern.startsWith("/api/v1/companies") || pattern.startsWith("/api/v1/drives")) {
                        publicRoutes.add(routeKey);
                    }
                }
            }
        }

        Collections.sort(appRoutes);
        Collections.sort(officerRoutes);
        Collections.sort(studentRoutes);

        // System.out.println("=== REGISTERED APPLICATION ROUTES (" + appRoutes.size() + ") ===");
        // appRoutes.forEach(System.out::println);

        // 1. Authoritative Total Application-Defined Route Count
        assertEquals(40, appRoutes.size(), "Application must define exactly 40 unique REST route mappings.");

        // 2. Authoritative Officer Route Count (22 across 5 controllers)
        assertEquals(22, officerRoutes.size(), "Officer endpoints must count exactly 22 routes across 5 controllers.");
        assertTrue(officerRoutes.contains("GET /api/v1/officer/companies"));
        assertTrue(officerRoutes.contains("GET /api/v1/officer/companies/{id}"));
        assertTrue(officerRoutes.contains("POST /api/v1/officer/companies"));
        assertTrue(officerRoutes.contains("PUT /api/v1/officer/companies/{id}"));
        assertTrue(officerRoutes.contains("GET /api/v1/officer/drives"));
        assertTrue(officerRoutes.contains("GET /api/v1/officer/drives/{id}"));
        assertTrue(officerRoutes.contains("POST /api/v1/officer/drives"));
        assertTrue(officerRoutes.contains("PUT /api/v1/officer/drives/{id}"));
        assertTrue(officerRoutes.contains("POST /api/v1/officer/drives/{id}/close"));
        assertTrue(officerRoutes.contains("GET /api/v1/officer/drives/{id}/applications"));
        assertTrue(officerRoutes.contains("GET /api/v1/officer/applications/{id}"));
        assertTrue(officerRoutes.contains("POST /api/v1/officer/applications/{id}/shortlist"));
        assertTrue(officerRoutes.contains("GET /api/v1/officer/interviewers"));
        assertTrue(officerRoutes.contains("POST /api/v1/officer/interviewers"));
        assertTrue(officerRoutes.contains("PUT /api/v1/officer/interviewers/{id}"));
        assertTrue(officerRoutes.contains("GET /api/v1/officer/rooms"));
        assertTrue(officerRoutes.contains("POST /api/v1/officer/rooms"));
        assertTrue(officerRoutes.contains("PUT /api/v1/officer/rooms/{id}"));
        assertTrue(officerRoutes.contains("POST /api/v1/officer/scheduling/preview"));
        assertTrue(officerRoutes.contains("POST /api/v1/officer/scheduling/publish"));
        assertTrue(officerRoutes.contains("GET /api/v1/officer/scheduling/published"));
        assertTrue(officerRoutes.contains("GET /api/v1/officer/scheduling/published/{id}"));

        // 3. Authoritative Student Route Count (9)
        assertEquals(9, studentRoutes.size(), "Student endpoints must count exactly 9 routes.");
        assertTrue(studentRoutes.contains("GET /api/v1/student/profile"));
        assertTrue(studentRoutes.contains("GET /api/v1/student/drives"));
        assertTrue(studentRoutes.contains("GET /api/v1/student/drives/{id}/eligibility"));
        assertTrue(studentRoutes.contains("GET /api/v1/student/applications"));
        assertTrue(studentRoutes.contains("GET /api/v1/student/applications/{id}"));
        assertTrue(studentRoutes.contains("POST /api/v1/student/applications"));
        assertTrue(studentRoutes.contains("GET /api/v1/student/dashboard"));
        assertTrue(studentRoutes.contains("GET /api/v1/student/interviews"));
        assertTrue(studentRoutes.contains("GET /api/v1/student/interviews/{id}"));

        // 4. Verify that NO unauthorized PUT /api/v1/student/profile or student mutation exists
        assertFalse(appRoutes.contains("PUT /api/v1/student/profile"), "PUT /api/v1/student/profile MUST NOT exist.");
        assertFalse(appRoutes.contains("PATCH /api/v1/student/profile"), "PATCH /api/v1/student/profile MUST NOT exist.");
        assertFalse(appRoutes.contains("GET /api/v1/student/drives/{id}"), "Direct GET /student/drives/{id} must not exist (use /eligibility).");

        // 5. Auth (4), Public Catalogs (4), Health (1)
        assertEquals(4, authRoutes.size());
        assertEquals(4, publicRoutes.size());
        assertEquals(1, healthRoutes.size());
    }

    @Test
    @DisplayName("Security: PUT /api/v1/student/profile is not mapped and returns 405 Method Not Allowed or 404")
    void testPutStudentProfile_rejected() throws Exception {
        mockMvc.perform(put("/api/v1/student/profile"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 405 || status == 404 || status == 401 || status == 403,
                            "Unsupported mutation PUT /student/profile must be rejected. Got status: " + status);
                });
    }
}
