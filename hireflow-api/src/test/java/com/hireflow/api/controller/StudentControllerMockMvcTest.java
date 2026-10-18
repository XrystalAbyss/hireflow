package com.hireflow.api.controller;

import com.hireflow.api.dto.ApplicationSubmitRequestDto;
import com.hireflow.api.security.HireFlowUserPrincipal;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Student;
import com.hireflow.repository.ApplicationRepository;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.repository.DriveRepository;
import com.hireflow.repository.StudentRepository;
import com.hireflow.service.ApplicationService;
import com.hireflow.web.security.PasswordHasher;
import com.hireflow.web.security.UserAccount;
import com.hireflow.web.security.UserAccountRepository;
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

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class StudentControllerMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private DriveRepository driveRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    private static final String STU_ID_1 = "STU-MOCK-001";
    private static final String STU_ID_2 = "STU-MOCK-002";
    private static final String CMP_ID_1 = "CMP-MOCK-001";
    private static final String DRV_OPEN_CSE = "DRV-MOCK-001";
    private static final String DRV_OPEN_MECH = "DRV-MOCK-002";
    private static final String DRV_CLOSED = "DRV-MOCK-003";
    private static final String DRV_EXPIRED = "DRV-MOCK-004";

    private Authentication aliceAuth;
    private Authentication bobAuth;
    private Authentication officerAuth;

    @BeforeEach
    void setUpTestData() {
        cleanAllTestData();

        // 1. Setup Student 1 (Alice: CSE, 8.50 CGPA)
        Student alice = new Student(STU_ID_1, "Alice Smith", "alice@hireflow.test", "CSE", 8.50, 2026, true);
        studentRepository.add(alice);

        // 2. Setup Student 2 (Bob: MECH, 6.00 CGPA)
        Student bob = new Student(STU_ID_2, "Bob Jones", "bob@hireflow.test", "MECH", 6.00, 2026, true);
        studentRepository.add(bob);

        // 3. Setup Company
        Company company = new Company(CMP_ID_1, "Apex Software Ltd", "Software Dev");
        companyRepository.add(company);

        // 4. Setup Drives
        // Drive 1: Open, CSE/ISE, min 7.0 CGPA, future deadline
        RecruitmentDrive cseDrive = new RecruitmentDrive(
                DRV_OPEN_CSE, CMP_ID_1, "Cloud Software Engineer", 7.0, Set.of("CSE", "ISE"), LocalDate.now().plusMonths(1), false
        );
        driveRepository.add(cseDrive);

        // Drive 2: Open, MECH, min 6.5 CGPA, future deadline
        RecruitmentDrive mechDrive = new RecruitmentDrive(
                DRV_OPEN_MECH, CMP_ID_1, "Robotics Engineer", 6.5, Set.of("MECH"), LocalDate.now().plusMonths(1), false
        );
        driveRepository.add(mechDrive);

        // Drive 3: Manually closed drive
        RecruitmentDrive closedDrive = new RecruitmentDrive(
                DRV_CLOSED, CMP_ID_1, "DevOps Specialist", 6.0, Set.of("CSE", "MECH"), LocalDate.now().plusMonths(1), true
        );
        driveRepository.add(closedDrive);

        // Drive 4: Expired deadline drive
        RecruitmentDrive expiredDrive = new RecruitmentDrive(
                DRV_EXPIRED, CMP_ID_1, "Security Analyst", 6.0, Set.of("CSE"), LocalDate.now().minusDays(5), false
        );
        driveRepository.add(expiredDrive);

        // 5. Setup User Accounts and Authentication tokens
        String hash = passwordHasher.hashPassword("Password@123".toCharArray());

        UserAccount aliceAccount = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username("alice_mock")
                .passwordHash(hash)
                .role(UserRole.STUDENT)
                .studentId(STU_ID_1)
                .displayName("Alice Smith")
                .enabled(true)
                .build();
        userAccountRepository.save(aliceAccount);

        UserAccount bobAccount = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username("bob_mock")
                .passwordHash(hash)
                .role(UserRole.STUDENT)
                .studentId(STU_ID_2)
                .displayName("Bob Jones")
                .enabled(true)
                .build();
        userAccountRepository.save(bobAccount);

        UserAccount officerAccount = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username("officer_mock")
                .passwordHash(hash)
                .role(UserRole.PLACEMENT_OFFICER)
                .displayName("Officer Dan")
                .enabled(true)
                .build();
        userAccountRepository.save(officerAccount);

        HireFlowUserPrincipal alicePrincipal = new HireFlowUserPrincipal(
                new UserPrincipal("alice_mock", "Alice Smith", UserRole.STUDENT, STU_ID_1)
        );
        aliceAuth = new UsernamePasswordAuthenticationToken(alicePrincipal, null, alicePrincipal.getAuthorities());

        HireFlowUserPrincipal bobPrincipal = new HireFlowUserPrincipal(
                new UserPrincipal("bob_mock", "Bob Jones", UserRole.STUDENT, STU_ID_2)
        );
        bobAuth = new UsernamePasswordAuthenticationToken(bobPrincipal, null, bobPrincipal.getAuthorities());

        HireFlowUserPrincipal officerPrincipal = new HireFlowUserPrincipal(
                new UserPrincipal("officer_mock", "Officer Dan", UserRole.PLACEMENT_OFFICER, null)
        );
        officerAuth = new UsernamePasswordAuthenticationToken(officerPrincipal, null, officerPrincipal.getAuthorities());
    }

    @AfterEach
    void cleanAllTestData() {
        try {
            applicationRepository.findByStudentId(STU_ID_1).forEach(a -> applicationRepository.deleteById(a.getId()));
            applicationRepository.findByStudentId(STU_ID_2).forEach(a -> applicationRepository.deleteById(a.getId()));

            driveRepository.deleteById(DRV_OPEN_CSE);
            driveRepository.deleteById(DRV_OPEN_MECH);
            driveRepository.deleteById(DRV_CLOSED);
            driveRepository.deleteById(DRV_EXPIRED);

            companyRepository.deleteById(CMP_ID_1);

            userAccountRepository.findByUsername("alice_mock").ifPresent(a -> userAccountRepository.deleteById(a.getId()));
            userAccountRepository.findByUsername("bob_mock").ifPresent(a -> userAccountRepository.deleteById(a.getId()));
            userAccountRepository.findByUsername("officer_mock").ifPresent(a -> userAccountRepository.deleteById(a.getId()));

            studentRepository.deleteById(STU_ID_1);
            studentRepository.deleteById(STU_ID_2);
        } catch (Exception ignored) {
        }
    }

    // =========================================================================
    // PART 5 — Student Profile Tests
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/student/profile: Authenticated student receives correct profile with anti-cache headers")
    void getProfile_authenticatedStudent_returnsProfile() throws Exception {
        mockMvc.perform(get("/api/v1/student/profile")
                        .with(authentication(aliceAuth)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.id").value(STU_ID_1))
                .andExpect(jsonPath("$.name").value("Alice Smith"))
                .andExpect(jsonPath("$.email").value("alice@hireflow.test"))
                .andExpect(jsonPath("$.branch").value("CSE"))
                .andExpect(jsonPath("$.cgpa").value(8.50))
                .andExpect(jsonPath("$.graduationYear").value(2026))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/student/profile: Anonymous request returns 401 Unauthorized")
    void getProfile_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/student/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("GET /api/v1/student/profile: Placement Officer returns 403 Forbidden")
    void getProfile_officerRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/student/profile")
                        .with(authentication(officerAuth)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    // =========================================================================
    // PART 6 — Drives Directory & Eligibility Tests
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/student/drives: Returns tailored drives with real-time academic eligibility")
    void getDrives_tailoredEligibility() throws Exception {
        mockMvc.perform(get("/api/v1/student/drives")
                        .with(authentication(aliceAuth)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))))
                // Open CSE drive: Alice (CSE, 8.5) is academically eligible and can apply
                .andExpect(jsonPath("$[?(@.id == '" + DRV_OPEN_CSE + "')].academicallyEligible").value(hasItem(true)))
                .andExpect(jsonPath("$[?(@.id == '" + DRV_OPEN_CSE + "')].canApply").value(hasItem(true)))
                .andExpect(jsonPath("$[?(@.id == '" + DRV_OPEN_CSE + "')].hasApplied").value(hasItem(false)))
                // Open MECH drive: Alice (CSE) is branch-ineligible
                .andExpect(jsonPath("$[?(@.id == '" + DRV_OPEN_MECH + "')].academicallyEligible").value(hasItem(false)))
                .andExpect(jsonPath("$[?(@.id == '" + DRV_OPEN_MECH + "')].canApply").value(hasItem(false)))
                // Closed drive: cannot apply
                .andExpect(jsonPath("$[?(@.id == '" + DRV_CLOSED + "')].canApply").value(hasItem(false)))
                .andExpect(jsonPath("$[?(@.id == '" + DRV_CLOSED + "')].closed").value(hasItem(true)))
                // Expired drive: cannot apply
                .andExpect(jsonPath("$[?(@.id == '" + DRV_EXPIRED + "')].canApply").value(hasItem(false)))
                .andExpect(jsonPath("$[?(@.id == '" + DRV_EXPIRED + "')].deadlinePassed").value(hasItem(true)));
    }

    @Test
    @DisplayName("GET /api/v1/student/drives/{id}/eligibility: Detailed eligibility check for eligible candidate")
    void getDriveEligibility_eligible() throws Exception {
        mockMvc.perform(get("/api/v1/student/drives/" + DRV_OPEN_CSE + "/eligibility")
                        .with(authentication(aliceAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driveId").value(DRV_OPEN_CSE))
                .andExpect(jsonPath("$.studentId").value(STU_ID_1))
                .andExpect(jsonPath("$.academicallyEligible").value(true))
                .andExpect(jsonPath("$.acceptingApplications").value(true))
                .andExpect(jsonPath("$.alreadyApplied").value(false))
                .andExpect(jsonPath("$.canApply").value(true))
                .andExpect(jsonPath("$.minimumCgpa").value(7.0))
                .andExpect(jsonPath("$.studentCgpa").value(8.50))
                .andExpect(jsonPath("$.studentBranch").value("CSE"))
                .andExpect(jsonPath("$.closed").value(false))
                .andExpect(jsonPath("$.deadlinePassed").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/student/drives/{id}/eligibility: Ineligible candidate due to branch and CGPA")
    void getDriveEligibility_ineligibleBranchAndCgpa() throws Exception {
        // Bob has MECH and 6.00 CGPA -> CSE Drive requires CSE/ISE and 7.00
        mockMvc.perform(get("/api/v1/student/drives/" + DRV_OPEN_CSE + "/eligibility")
                        .with(authentication(bobAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driveId").value(DRV_OPEN_CSE))
                .andExpect(jsonPath("$.studentId").value(STU_ID_2))
                .andExpect(jsonPath("$.academicallyEligible").value(false))
                .andExpect(jsonPath("$.canApply").value(false))
                .andExpect(jsonPath("$.reasons", hasItem(containsString("CGPA"))))
                .andExpect(jsonPath("$.reasons", hasItem(containsString("branch"))));
    }

    @Test
    @DisplayName("GET /api/v1/student/drives/{id}/eligibility: Non-existent drive returns 404")
    void getDriveEligibility_nonExistent_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/student/drives/DRV-DOES-NOT-EXIST/eligibility")
                        .with(authentication(aliceAuth)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // =========================================================================
    // PART 7 & 8 — Application Submission & Tracking Tests
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/student/applications: Valid submission succeeds with 201 Created and Location header")
    void submitApplication_success() throws Exception {
        mockMvc.perform(post("/api/v1/student/applications")
                        .with(csrf())
                        .with(authentication(aliceAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driveId\":\"" + DRV_OPEN_CSE + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/student/applications/")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.studentId").value(STU_ID_1))
                .andExpect(jsonPath("$.driveId").value(DRV_OPEN_CSE))
                .andExpect(jsonPath("$.companyName").value("Apex Software Ltd"))
                .andExpect(jsonPath("$.jobTitle").value("Cloud Software Engineer"))
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.id", not(emptyOrNullString())));

        assertTrue(applicationRepository.existsByStudentAndDrive(STU_ID_1, DRV_OPEN_CSE));
    }

    @Test
    @DisplayName("POST /api/v1/student/applications: Missing CSRF token is rejected with 403 Forbidden")
    void submitApplication_missingCsrf_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/student/applications")
                        .with(authentication(aliceAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driveId\":\"" + DRV_OPEN_CSE + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/student/applications: Ineligible candidate submission rejected with 400 Bad Request")
    void submitApplication_ineligible_returns400() throws Exception {
        // Bob is MECH with 6.00 CGPA -> CSE Drive requires CSE/ISE and 7.00
        mockMvc.perform(post("/api/v1/student/applications")
                        .with(csrf())
                        .with(authentication(bobAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driveId\":\"" + DRV_OPEN_CSE + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("not eligible")));
    }

    @Test
    @DisplayName("POST /api/v1/student/applications: Closed drive submission rejected with 400 Bad Request")
    void submitApplication_closedDrive_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/student/applications")
                        .with(csrf())
                        .with(authentication(aliceAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driveId\":\"" + DRV_CLOSED + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("closed for applications")));
    }

    @Test
    @DisplayName("POST /api/v1/student/applications: Expired deadline drive submission rejected with 400 Bad Request")
    void submitApplication_expiredDeadline_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/student/applications")
                        .with(csrf())
                        .with(authentication(aliceAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driveId\":\"" + DRV_EXPIRED + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("deadline")));
    }

    @Test
    @DisplayName("POST /api/v1/student/applications: Duplicate submission rejected with 409 Conflict")
    void submitApplication_duplicate_returns409() throws Exception {
        // 1. First submission succeeds
        mockMvc.perform(post("/api/v1/student/applications")
                        .with(csrf())
                        .with(authentication(aliceAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driveId\":\"" + DRV_OPEN_CSE + "\"}"))
                .andExpect(status().isCreated());

        // 2. Second submission returns 409 Conflict
        mockMvc.perform(post("/api/v1/student/applications")
                        .with(csrf())
                        .with(authentication(aliceAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driveId\":\"" + DRV_OPEN_CSE + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("already applied")));
    }

    @Test
    @DisplayName("GET /api/v1/student/applications: Bounded pagination and deterministic sorting")
    void getApplications_pagedAndSorted() throws Exception {
        applicationService.submitApplication(STU_ID_1, DRV_OPEN_CSE);

        mockMvc.perform(get("/api/v1/student/applications?page=0&size=10&sort=appliedAt,desc")
                        .with(authentication(aliceAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].studentId").value(STU_ID_1))
                .andExpect(jsonPath("$.content[0].driveId").value(DRV_OPEN_CSE))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    @DisplayName("GET /api/v1/student/applications/{id}: Strict ownership enforcement - other student receives 404")
    void getApplicationById_crossStudentIsolation_returns404() throws Exception {
        // Alice submits an application
        var aliceApp = applicationService.submitApplication(STU_ID_1, DRV_OPEN_CSE);

        // 1. Alice can view her own application (200 OK)
        mockMvc.perform(get("/api/v1/student/applications/" + aliceApp.getId())
                        .with(authentication(aliceAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(aliceApp.getId()))
                .andExpect(jsonPath("$.studentId").value(STU_ID_1));

        // 2. Bob attempts to view Alice's application -> returns 404 Not Found (resource not disclosed!)
        mockMvc.perform(get("/api/v1/student/applications/" + aliceApp.getId())
                        .with(authentication(bobAuth)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/v1/student/dashboard: Summarizes eligible drives and active applications")
    void getDashboard_returnsSummary() throws Exception {
        applicationService.submitApplication(STU_ID_1, DRV_OPEN_CSE);

        mockMvc.perform(get("/api/v1/student/dashboard")
                        .with(authentication(aliceAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.id").value(STU_ID_1))
                .andExpect(jsonPath("$.totalApplications").value(1))
                .andExpect(jsonPath("$.recentApplications", hasSize(1)));
    }
}
