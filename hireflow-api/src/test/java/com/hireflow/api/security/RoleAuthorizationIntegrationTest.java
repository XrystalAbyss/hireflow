package com.hireflow.api.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RoleAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Unauthenticated request to protected endpoint returns 401 Unauthorized")
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/test/student-only"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @WithMockUser(username = "student_alex", roles = {"STUDENT"})
    @DisplayName("STUDENT role can access student endpoints but is denied officer and admin routes")
    void studentRole_accessBoundaries() throws Exception {
        mockMvc.perform(get("/api/v1/test/student-only"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("GRANTED"));

        mockMvc.perform(get("/api/v1/test/officer-only"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));

        mockMvc.perform(get("/api/v1/test/admin-only"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(username = "officer_pat", roles = {"PLACEMENT_OFFICER"})
    @DisplayName("PLACEMENT_OFFICER role can access officer endpoints but is denied student and admin routes")
    void placementOfficerRole_accessBoundaries() throws Exception {
        mockMvc.perform(get("/api/v1/test/officer-only"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("GRANTED"));

        mockMvc.perform(get("/api/v1/test/student-only"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(get("/api/v1/test/admin-only"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(username = "admin_root", roles = {"ADMINISTRATOR", "PLACEMENT_OFFICER"})
    @DisplayName("ADMINISTRATOR role can access admin and officer endpoints but is denied student routes")
    void administratorRole_accessBoundaries() throws Exception {
        mockMvc.perform(get("/api/v1/test/admin-only"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("GRANTED"));

        mockMvc.perform(get("/api/v1/test/officer-only"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("GRANTED"));

        mockMvc.perform(get("/api/v1/test/student-only"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @WithMockUser(username = "student_alex", roles = {"STUDENT"})
    @DisplayName("Client query parameters cannot elevate role privileges")
    void clientQueryParamsCannotOverrideRoles() throws Exception {
        mockMvc.perform(get("/api/v1/test/admin-only").param("role", "ADMINISTRATOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(containsString("insufficient role privileges")));
    }
}
