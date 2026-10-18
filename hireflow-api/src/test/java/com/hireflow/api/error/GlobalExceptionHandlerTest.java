package com.hireflow.api.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    @DisplayName("HTTP POST to read-only GET endpoint returns 405 Method Not Allowed")
    void methodNotAllowedReturns405() throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Unauthorized Write Corp\"}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(jsonPath("$.message").value(containsString("HTTP method POST is not supported")));
    }

    @Test
    @WithMockUser
    @DisplayName("HTTP DELETE to read-only GET endpoint returns 405 Method Not Allowed")
    void deleteMethodNotAllowedReturns405() throws Exception {
        mockMvc.perform(delete("/api/v1/drives/DRV-001")
                        .with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }

    @Test
    @DisplayName("State-changing request without CSRF token returns 403 Forbidden")
    void stateChangingRequestWithoutCsrfReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value(containsString("CSRF")));
    }

    @Test
    @DisplayName("Sanitization: Error response never exposes database passwords, hostnames or JDBC URLs")
    void errorResponseSuppressesSensitiveInfrastructure() throws Exception {
        mockMvc.perform(get("/api/v1/companies")
                        .param("page", "-999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", not(containsString("jdbc:postgresql"))))
                .andExpect(jsonPath("$.message", not(containsString("password"))))
                .andExpect(jsonPath("$.message", not(containsString("org.postgresql"))));
    }
}
