package com.hireflow.api.controller;

import com.hireflow.api.security.HireFlowUserPrincipal;
import com.hireflow.model.*;
import com.hireflow.repository.*;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OfficerControllerMockMvcTest {
    @org.springframework.beans.factory.annotation.Autowired
    private javax.sql.DataSource cleanupDataSource;


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
    private InterviewerRepository interviewerRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    private static final String CMP_ID = "CMP-OFF-01";
    private static final String DRV_ID = "DRV-OFF-01";
    private static final String STU_ID = "STU-OFF-01";
    private static final String APP_ID = "APP-OFF-01";
    private static final String INT_ID = "INT-OFF-01";
    private static final String ROOM_ID = "RM-OFF-01";

    private Authentication officerAuth;
    private Authentication studentAuth;

    @BeforeEach
    void setUpTestData() {
        cleanAllTestData();

        // 1. Setup Company
        Company company = new Company(CMP_ID, "Titan Global Tech", "Enterprise Systems");
        companyRepository.add(company);

        // 2. Setup Student
        Student student = new Student(STU_ID, "David Warner", "david@hireflow.test", "CSE", 8.80, 2026, true);
        studentRepository.add(student);

        // 3. Setup Drive
        RecruitmentDrive drive = new RecruitmentDrive(
                DRV_ID, CMP_ID, "Backend Systems Engineer", 7.50, Set.of("CSE", "ISE"), LocalDate.now().plusMonths(1), false
        );
        driveRepository.add(drive);

        // 4. Setup Application
        Application app = new Application(
                APP_ID, STU_ID, DRV_ID, ApplicationStatus.APPLIED, java.time.LocalDateTime.now(), "Initial submission."
        );
        applicationRepository.add(app);

        // 5. Setup Interviewer
        Interviewer interviewer = new Interviewer(INT_ID, "Dr. Sarah Connor", CMP_ID);
        interviewerRepository.add(interviewer);

        // 6. Setup Room
        Room room = new Room(ROOM_ID, "Main Interview Suite A", 10);
        roomRepository.add(room);

        // 7. Setup Accounts
        String hash = passwordHasher.hashPassword("SecurePass@2026".toCharArray());

        UserAccount officerAccount = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username("officer_dan")
                .passwordHash(hash)
                .role(UserRole.PLACEMENT_OFFICER)
                .displayName("Officer Dan")
                .enabled(true)
                .build();
        userAccountRepository.save(officerAccount);

        UserAccount studentAccount = UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username("david_student")
                .passwordHash(hash)
                .role(UserRole.STUDENT)
                .studentId(STU_ID)
                .displayName("David Warner")
                .enabled(true)
                .build();
        userAccountRepository.save(studentAccount);

        HireFlowUserPrincipal officerPrincipal = new HireFlowUserPrincipal(
                new UserPrincipal("officer_dan", "Officer Dan", UserRole.PLACEMENT_OFFICER, null)
        );
        officerAuth = new UsernamePasswordAuthenticationToken(officerPrincipal, null, officerPrincipal.getAuthorities());

        HireFlowUserPrincipal studentPrincipal = new HireFlowUserPrincipal(
                new UserPrincipal("david_student", "David Warner", UserRole.STUDENT, STU_ID)
        );
        studentAuth = new UsernamePasswordAuthenticationToken(studentPrincipal, null, studentPrincipal.getAuthorities());
    }

    @AfterEach
    void tearDown() {
        cleanAllTestData();
    }

    private void cleanAllTestData() {
        com.hireflow.api.testsupport.SafeTestDatabase.clean(cleanupDataSource,
            new String[]{"CMP-OFF-01", "DRV-OFF-01", "STU-OFF-01", "APP-OFF-01", "INT-OFF-01", "RM-OFF-01", "DRV-NEW-01", "CMP-NEW-01", "INT-NEW-01", "RM-NEW-01"},
            new String[]{"officer_dan", "david_student"});
    }

    // ==========================================
    // SECURITY & ACCESS CONTROL
    // ==========================================

    @Test
    @DisplayName("Anonymous access to officer routes is rejected with 401 Unauthorized")
    void testAnonymousAccessRejected() throws Exception {
        mockMvc.perform(get("/api/v1/officer/companies"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/officer/drives"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/officer/interviewers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Student access to officer routes is rejected with 403 Forbidden")
    void testStudentAccessRejected() throws Exception {
        mockMvc.perform(get("/api/v1/officer/companies").with(authentication(studentAuth)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/officer/drives").with(authentication(studentAuth)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Mutating POST without CSRF token returns 403 Forbidden")
    void testMutatingWithoutCsrfRejected() throws Exception {
        String json = """
                {"id":"CMP-NEW-01","name":"New Tech Corp","industry":"Tech"}
                """;
        mockMvc.perform(post("/api/v1/officer/companies")
                        .with(authentication(officerAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // COMPANIES
    // ==========================================

    @Test
    @DisplayName("Officer can list companies with pagination")
    void testGetCompanies() throws Exception {
        mockMvc.perform(get("/api/v1/officer/companies")
                        .with(authentication(officerAuth)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[?(@.id == '" + CMP_ID + "')].name", contains("Titan Global Tech")));
    }

    @Test
    @DisplayName("Officer can get company by ID")
    void testGetCompanyById() throws Exception {
        mockMvc.perform(get("/api/v1/officer/companies/" + CMP_ID)
                        .with(authentication(officerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CMP_ID))
                .andExpect(jsonPath("$.name").value("Titan Global Tech"))
                .andExpect(jsonPath("$.totalDrivesCount").value(1));
    }

    @Test
    @DisplayName("Officer get nonexistent company returns 404")
    void testGetUnknownCompanyReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/officer/companies/UNKNOWN_CMP")
                        .with(authentication(officerAuth)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Officer can register a new company")
    void testCreateCompany() throws Exception {
        String json = """
                {"id":"CMP-NEW-01","name":"NextGen Aerospace","industry":"Aerospace"}
                """;
        mockMvc.perform(post("/api/v1/officer/companies")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/officer/companies/CMP-NEW-01"))
                .andExpect(jsonPath("$.id").value("CMP-NEW-01"))
                .andExpect(jsonPath("$.name").value("NextGen Aerospace"));
    }

    @Test
    @DisplayName("Officer create company with blank name returns 400")
    void testCreateCompanyBlankNameReturns400() throws Exception {
        String json = """
                {"id":"CMP-NEW-01","name":"","industry":"Aerospace"}
                """;
        mockMvc.perform(post("/api/v1/officer/companies")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Officer create duplicate company returns 409 Conflict")
    void testCreateDuplicateCompanyReturns409() throws Exception {
        String json = """
                {"id":"%s","name":"Another Name","industry":"Tech"}
                """.formatted(CMP_ID);
        mockMvc.perform(post("/api/v1/officer/companies")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Officer can update existing company")
    void testUpdateCompany() throws Exception {
        String json = """
                {"name":"Titan Tech International","industry":"Cloud Computing"}
                """;
        mockMvc.perform(put("/api/v1/officer/companies/" + CMP_ID)
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Titan Tech International"))
                .andExpect(jsonPath("$.industry").value("Cloud Computing"));
    }

    // ==========================================
    // RECRUITMENT DRIVES
    // ==========================================

    @Test
    @DisplayName("Officer can list recruitment drives")
    void testGetDrives() throws Exception {
        mockMvc.perform(get("/api/v1/officer/drives")
                        .with(authentication(officerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[?(@.id == '" + DRV_ID + "')].companyName", contains("Titan Global Tech")));
    }

    @Test
    @DisplayName("Officer can get drive by ID")
    void testGetDriveById() throws Exception {
        mockMvc.perform(get("/api/v1/officer/drives/" + DRV_ID)
                        .with(authentication(officerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(DRV_ID))
                .andExpect(jsonPath("$.companyName").value("Titan Global Tech"))
                .andExpect(jsonPath("$.closed").value(false))
                .andExpect(jsonPath("$.totalApplications").value(1));
    }

    @Test
    @DisplayName("Officer can create a new recruitment drive")
    void testCreateDrive() throws Exception {
        String json = """
                {
                    "id": "DRV-NEW-01",
                    "companyId": "%s",
                    "jobTitle": "Full Stack Lead",
                    "minimumCgpa": 8.0,
                    "eligibleBranches": ["CSE", "ECE"],
                    "applicationDeadline": "%s"
                }
                """.formatted(CMP_ID, LocalDate.now().plusMonths(2));
        mockMvc.perform(post("/api/v1/officer/drives")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/officer/drives/DRV-NEW-01"))
                .andExpect(jsonPath("$.id").value("DRV-NEW-01"))
                .andExpect(jsonPath("$.jobTitle").value("Full Stack Lead"));
    }

    @Test
    @DisplayName("Officer create drive with unknown company returns 404")
    void testCreateDriveUnknownCompanyReturns404() throws Exception {
        String json = """
                {
                    "id": "DRV-NEW-01",
                    "companyId": "NON_EXISTENT_CMP",
                    "jobTitle": "Architect",
                    "minimumCgpa": 8.0,
                    "eligibleBranches": ["CSE"],
                    "applicationDeadline": "%s"
                }
                """.formatted(LocalDate.now().plusMonths(1));
        mockMvc.perform(post("/api/v1/officer/drives")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Officer create drive with invalid CGPA returns 400")
    void testCreateDriveInvalidCgpaReturns400() throws Exception {
        String json = """
                {
                    "id": "DRV-NEW-01",
                    "companyId": "%s",
                    "jobTitle": "Architect",
                    "minimumCgpa": 11.5,
                    "eligibleBranches": ["CSE"],
                    "applicationDeadline": "%s"
                }
                """.formatted(CMP_ID, LocalDate.now().plusMonths(1));
        mockMvc.perform(post("/api/v1/officer/drives")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Officer can update existing recruitment drive")
    void testUpdateDrive() throws Exception {
        String json = """
                {
                    "jobTitle": "Lead Backend Architect",
                    "minimumCgpa": 8.25,
                    "eligibleBranches": ["CSE", "IT"],
                    "applicationDeadline": "%s"
                }
                """.formatted(LocalDate.now().plusMonths(3));
        mockMvc.perform(put("/api/v1/officer/drives/" + DRV_ID)
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Lead Backend Architect"))
                .andExpect(jsonPath("$.minimumCgpa").value(8.25));
    }

    @Test
    @DisplayName("Officer can close recruitment drive and repeat-close is idempotent")
    void testCloseDriveAndRepeatClose() throws Exception {
        // First close
        mockMvc.perform(post("/api/v1/officer/drives/" + DRV_ID + "/close")
                        .with(authentication(officerAuth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(true));

        // Repeat close is idempotent
        mockMvc.perform(post("/api/v1/officer/drives/" + DRV_ID + "/close")
                        .with(authentication(officerAuth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(true));

        // Attempting to update closed drive is rejected with 400 Bad Request
        String updateJson = """
                {
                    "jobTitle": "Lead Backend Architect",
                    "minimumCgpa": 8.0,
                    "eligibleBranches": ["CSE"],
                    "applicationDeadline": "%s"
                }
                """.formatted(LocalDate.now().plusMonths(3));
        mockMvc.perform(put("/api/v1/officer/drives/" + DRV_ID)
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("drive is closed")));
    }

    // ==========================================
    // APPLICATIONS & SHORTLISTING
    // ==========================================

    @Test
    @DisplayName("Officer can list applications for a drive with student academic details")
    void testGetApplicationsForDrive() throws Exception {
        mockMvc.perform(get("/api/v1/officer/drives/" + DRV_ID + "/applications")
                        .with(authentication(officerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].studentName").value("David Warner"))
                .andExpect(jsonPath("$.content[0].cgpa").value(8.80))
                .andExpect(jsonPath("$.content[0].status").value("APPLIED"));
    }

    @Test
    @DisplayName("Officer can get application by ID")
    void testGetApplicationById() throws Exception {
        mockMvc.perform(get("/api/v1/officer/applications/" + APP_ID)
                        .with(authentication(officerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(APP_ID))
                .andExpect(jsonPath("$.studentName").value("David Warner"))
                .andExpect(jsonPath("$.status").value("APPLIED"));
    }

    @Test
    @DisplayName("Officer can shortlist candidate and repeat-shortlist returns 409 Conflict")
    void testShortlistApplication() throws Exception {
        // First shortlist succeeds
        mockMvc.perform(post("/api/v1/officer/applications/" + APP_ID + "/shortlist")
                        .with(authentication(officerAuth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHORTLISTED"));

        // Repeat shortlist returns 409 Conflict
        mockMvc.perform(post("/api/v1/officer/applications/" + APP_ID + "/shortlist")
                        .with(authentication(officerAuth))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("already been shortlisted")));
    }

    // ==========================================
    // INTERVIEW RESOURCES (INTERVIEWERS & ROOMS)
    // ==========================================

    @Test
    @DisplayName("Officer can list and create corporate interviewers")
    void testInterviewerOperations() throws Exception {
        mockMvc.perform(get("/api/v1/officer/interviewers")
                        .with(authentication(officerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        String json = """
                {"id":"INT-NEW-01","name":"Kyle Reese","companyId":"%s"}
                """.formatted(CMP_ID);
        mockMvc.perform(post("/api/v1/officer/interviewers")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/officer/interviewers/INT-NEW-01"))
                .andExpect(jsonPath("$.id").value("INT-NEW-01"))
                .andExpect(jsonPath("$.name").value("Kyle Reese"));
    }

    @Test
    @DisplayName("Officer can list and create interview rooms")
    void testRoomOperations() throws Exception {
        mockMvc.perform(get("/api/v1/officer/rooms")
                        .with(authentication(officerAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        String json = """
                {"id":"RM-NEW-01","name":"Conference Room Alpha","capacity":15}
                """;
        mockMvc.perform(post("/api/v1/officer/rooms")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/officer/rooms/RM-NEW-01"))
                .andExpect(jsonPath("$.id").value("RM-NEW-01"))
                .andExpect(jsonPath("$.capacity").value(15));
    }

    @Test
    @DisplayName("Officer create room with negative capacity returns 400")
    void testCreateRoomNegativeCapacityReturns400() throws Exception {
        String json = """
                {"id":"RM-NEW-01","name":"Bad Room","capacity":-5}
                """;
        mockMvc.perform(post("/api/v1/officer/rooms")
                        .with(authentication(officerAuth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
}
