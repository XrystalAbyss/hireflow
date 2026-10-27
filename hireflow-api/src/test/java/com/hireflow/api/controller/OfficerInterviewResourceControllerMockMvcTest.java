package com.hireflow.api.controller;

import com.hireflow.api.security.HireFlowUserPrincipal;
import com.hireflow.web.security.UserPrincipal;
import com.hireflow.web.security.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc Test Suite for OfficerInterviewResourceController.
 * Validates interview resource management (interviewers and rooms), campus room policy
 * enforcement (approved 36-room inventory, rejection of faculty/lab/arbitrary codes),
 * authorization constraints, and capacity bounds.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OfficerInterviewResourceControllerMockMvcTest {

    @Autowired
    private javax.sql.DataSource cleanupDataSource;

    @Autowired
    private MockMvc mockMvc;

    private Authentication officerAuth;
    private Authentication studentAuth;

    @BeforeEach
    void setUpTestData() {
        cleanTestData();

        HireFlowUserPrincipal officerPrincipal = new HireFlowUserPrincipal(
                new UserPrincipal("demo.officer", "Demo Placement Officer", UserRole.PLACEMENT_OFFICER, null)
        );
        officerAuth = new UsernamePasswordAuthenticationToken(officerPrincipal, null, officerPrincipal.getAuthorities());

        HireFlowUserPrincipal studentPrincipal = new HireFlowUserPrincipal(
                new UserPrincipal("demo.student", "Demo Student", UserRole.STUDENT, "DEMO-STU-01")
        );
        studentAuth = new UsernamePasswordAuthenticationToken(studentPrincipal, null, studentPrincipal.getAuthorities());
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    private void cleanTestData() {
        com.hireflow.api.testsupport.SafeTestDatabase.clean(cleanupDataSource,
                new String[]{"INT-NEW-01"},
                new String[]{});
    }

    // ==========================================
    // ROOM AUDIT TESTS
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/officer/rooms: returns list of rooms with 200 OK")
    void testGetRooms_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/officer/rooms")
                        .with(authentication(officerAuth)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", isA(java.util.List.class)))
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(36)));
    }

    @Test
    @DisplayName("POST /api/v1/officer/rooms: existing approved room KF02 returns 409 Conflict")
    void testCreateRoom_existingApprovedRoom_returns409() throws Exception {
        String json = """
                {
                    "id": "KF02",
                    "name": "K Block First Floor - Room KF02",
                    "capacity": 4
                }
                """;

        mockMvc.perform(post("/api/v1/officer/rooms")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    @DisplayName("POST /api/v1/officer/rooms: rejects faculty/amenity room KF01 with 400 Bad Request")
    void testCreateRoom_facultyRoom_returns422() throws Exception {
        String json = """
                {
                    "id": "KF01",
                    "name": "Faculty Office KF01",
                    "capacity": 4
                }
                """;

        mockMvc.perform(post("/api/v1/officer/rooms")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("designated for faculty")));
    }

    @Test
    @DisplayName("POST /api/v1/officer/rooms: rejects ground-floor lab K-LAB-1 with 400 Bad Request")
    void testCreateRoom_laboratory_returns422() throws Exception {
        String json = """
                {
                    "id": "K-LAB-1",
                    "name": "K Block Ground Floor Laboratory 1",
                    "capacity": 30
                }
                """;

        mockMvc.perform(post("/api/v1/officer/rooms")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("laboratories cannot be registered")));
    }

    @Test
    @DisplayName("POST /api/v1/officer/rooms: rejects arbitrary unapproved room code RM-ARBITRARY with 400 Bad Request")
    void testCreateRoom_arbitraryLegacyPrefix_returns422() throws Exception {
        String json = """
                {
                    "id": "RM-ARBITRARY-99",
                    "name": "Arbitrary Ad-hoc Interview Suite",
                    "capacity": 4
                }
                """;

        mockMvc.perform(post("/api/v1/officer/rooms")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not an approved university engineering interview venue")));
    }

    @Test
    @DisplayName("POST /api/v1/officer/rooms: rejects historical legacy identifier RM-101 with 400 Bad Request")
    void testCreateRoom_legacyRoomIdentifier_returnsBadRequest() throws Exception {
        String json = """
                {
                    "id": "RM-101",
                    "name": "Historical Room 101",
                    "capacity": 4
                }
                """;

        mockMvc.perform(post("/api/v1/officer/rooms")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Legacy and test identifiers cannot be used for new room registration")));
    }

    @Test
    @DisplayName("POST /api/v1/officer/rooms: rejects zero or negative capacity with 400 Bad Request")
    void testCreateRoom_zeroCapacity_returns400() throws Exception {
        String json = """
                {
                    "id": "KF02",
                    "name": "K Block First Floor - Room KF02",
                    "capacity": 0
                }
                """;

        mockMvc.perform(post("/api/v1/officer/rooms")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("at least 1")));
    }

    @Test
    @DisplayName("PUT /api/v1/officer/rooms/{id}: successfully updates capacity on existing room KF02 with 200 OK")
    void testUpdateRoom_valid_returns200() throws Exception {
        String json = """
                {
                    "name": "K Block First Floor - Room KF02 Renovation",
                    "capacity": 6
                }
                """;

        mockMvc.perform(put("/api/v1/officer/rooms/KF02")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("KF02")))
                .andExpect(jsonPath("$.capacity", is(6)))
                .andExpect(jsonPath("$.name", is("K Block First Floor - Room KF02 Renovation")));
    }

    @Test
    @DisplayName("POST /api/v1/officer/rooms: student role is rejected with 403 Forbidden")
    void testCreateRoom_asStudent_returns403() throws Exception {
        String json = """
                {
                    "id": "KF02",
                    "name": "K Block First Floor - Room KF02",
                    "capacity": 4
                }
                """;

        mockMvc.perform(post("/api/v1/officer/rooms")
                        .with(authentication(studentAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/officer/rooms: anonymous request is rejected with 401 Unauthorized")
    void testCreateRoom_anonymous_returns401() throws Exception {
        String json = """
                {
                    "id": "KF02",
                    "name": "K Block First Floor - Room KF02",
                    "capacity": 4
                }
                """;

        mockMvc.perform(post("/api/v1/officer/rooms")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized());
    }

    // ==========================================
    // INTERVIEWER AUDIT TESTS
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/officer/interviewers: returns list of corporate interviewers with 200 OK")
    void testGetInterviewers_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/officer/interviewers")
                        .with(authentication(officerAuth)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", isA(java.util.List.class)))
                .andExpect(jsonPath("$[0].name", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/officer/interviewers: registers new interviewer with 201 Created")
    void testCreateInterviewer_valid_returns201() throws Exception {
        String json = """
                {
                    "id": "INT-NEW-01",
                    "name": "Prof. Marcus Thorne",
                    "companyId": "CMP-U26-01"
                }
                """;

        mockMvc.perform(post("/api/v1/officer/interviewers")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/officer/interviewers/INT-NEW-01"))
                .andExpect(jsonPath("$.id", is("INT-NEW-01")))
                .andExpect(jsonPath("$.name", is("Prof. Marcus Thorne")))
                .andExpect(jsonPath("$.companyName", is("Aether Cloud Networks")));
    }
}
