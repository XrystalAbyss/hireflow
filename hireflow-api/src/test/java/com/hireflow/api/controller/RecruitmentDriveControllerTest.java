package com.hireflow.api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RecruitmentDriveControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/drives returns 200 OK with paginated drive list")
    void listDrivesReturnsPagedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/drives")
                        .param("page", "0")
                        .param("size", "10")
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
    @DisplayName("GET /api/v1/drives with activeOnly=true and companyId filters correctly")
    void listDrivesWithFilters() throws Exception {
        mockMvc.perform(get("/api/v1/drives")
                        .param("companyId", "CMP-001")
                        .param("activeOnly", "true")
                        .param("page", "0")
                        .param("size", "5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/drives?page=-1 returns 400 Bad Request")
    void negativePageReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/drives")
                        .param("page", "-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("cannot be negative")));
    }

    @Test
    @DisplayName("GET /api/v1/drives?size=500 returns 400 Bad Request (bounded pagination)")
    void excessiveSizeReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/drives")
                        .param("size", "500")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("must be between 1 and 100")));
    }

    @Test
    @DisplayName("GET /api/v1/drives?sortBy=unsupportedField returns 400 Bad Request")
    void unsupportedSortByReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/drives")
                        .param("sortBy", "confidentialSalary")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Invalid sort parameter")));
    }

    @Test
    @DisplayName("GET /api/v1/drives/NON-EXISTENT-DRIVE returns 404 Not Found")
    void unknownDriveReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/drives/NON-EXISTENT-DRIVE-999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(containsString("was not found")))
                .andExpect(jsonPath("$.path").value("/api/v1/drives/NON-EXISTENT-DRIVE-999"));
    }

    @Test
    @DisplayName("Security Check: Drives API strictly suppresses sensitive student and timetable data")
    void driveEndpointNeverExposesCandidateOrTimetableData() throws Exception {
        String responseBody = mockMvc.perform(get("/api/v1/drives")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Verify that NO student personal info or internal application state is exposed
        assertFalse(responseBody.contains("passwordHash"), "Must not expose password hash");
        assertFalse(responseBody.contains("applications"), "Must not expose internal application list");
        assertFalse(responseBody.contains("studentId"), "Must not expose private student IDs");
        assertFalse(responseBody.contains("interviewerSessions"), "Must not expose interview sessions");
        assertFalse(responseBody.contains("roomAssignments"), "Must not expose room assignments");
    }
}
