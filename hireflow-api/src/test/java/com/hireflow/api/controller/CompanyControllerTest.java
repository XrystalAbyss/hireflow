package com.hireflow.api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CompanyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/companies returns 200 OK with paginated collection")
    void listCompaniesReturnsPagedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/companies")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "name")
                        .param("direction", "ASC")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    @DisplayName("GET /api/v1/companies?page=-1 returns 400 Bad Request with ApiErrorResponse")
    void negativePageReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/companies")
                        .param("page", "-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("cannot be negative")))
                .andExpect(jsonPath("$.path").value("/api/v1/companies"));
    }

    @Test
    @DisplayName("GET /api/v1/companies?size=150 returns 400 Bad Request (bounded pagination)")
    void oversizedSizeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/companies")
                        .param("size", "150")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("must be between 1 and 100")));
    }

    @Test
    @DisplayName("GET /api/v1/companies?sortBy=invalidField returns 400 Bad Request")
    void invalidSortByReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/companies")
                        .param("sortBy", "creditCardNumber")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Invalid sort parameter")));
    }

    @Test
    @DisplayName("GET /api/v1/companies?direction=UNKNOWN returns 400 Bad Request")
    void invalidDirectionReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/companies")
                        .param("direction", "SIDEWAYS")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Invalid direction")));
    }

    @Test
    @DisplayName("GET /api/v1/companies/UNKNOWN-CORP-ID returns 404 Not Found")
    void unknownCompanyReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/companies/NON-EXISTENT-XYZ-999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("was not found")))
                .andExpect(jsonPath("$.path").value("/api/v1/companies/NON-EXISTENT-XYZ-999"));
    }
}
