package com.hireflow.api.controller;

import com.hireflow.api.security.HireFlowUserPrincipal;
import com.hireflow.model.*;
import com.hireflow.repository.*;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("StudentInterviewControllerMockMvcTest - Authenticated Student Interview Timetable Retrieval and Cross-Student Isolation")
class StudentInterviewControllerMockMvcTest {

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
    private InterviewSessionRepository sessionRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private javax.sql.DataSource dataSource;

    private Authentication studentDianaAuth;
    private Authentication studentClarkAuth;
    private Authentication officerAuth;

    private static final String CMP_ID = "CMP-STU-INT";
    private static final String DRV_ID = "DRV-STU-INT";
    private static final String STU_1_ID = "STU-STU-INT1";
    private static final String STU_2_ID = "STU-STU-INT2";
    private static final String APP_1_ID = "APP-STU-INT1";
    private static final String APP_2_ID = "APP-STU-INT2";
    private static final String INT_1_ID = "INT-STU-INT1";
    private static final String ROOM_1_ID = "RM-STU-INT1";
    private static final String SESS_1_ID = "SES-STU-INT1";
    private static final String SESS_2_ID = "SES-STU-INT2";

    @BeforeEach
    void setUp() {
        cleanDatabase();

        // 1. Company
        companyRepository.add(new Company(CMP_ID, "Orion Cybernetics", "AI Systems"));

        // 2. Drive
        driveRepository.add(new RecruitmentDrive(
                DRV_ID, CMP_ID, "AI Research Engineer", 7.50, Set.of("CSE"),
                LocalDate.now().plusMonths(1), false
        ));

        // 3. Students
        studentRepository.add(new Student(STU_1_ID, "Diana Prince", "diana@hireflow.test", "CSE", 9.20, 2026, true));
        studentRepository.add(new Student(STU_2_ID, "Clark Kent", "clark@hireflow.test", "CSE", 8.90, 2026, true));

        // 4. Applications
        applicationRepository.add(new Application(APP_1_ID, STU_1_ID, DRV_ID, ApplicationStatus.SCHEDULED, LocalDateTime.now().minusDays(3), "Scheduled"));
        applicationRepository.add(new Application(APP_2_ID, STU_2_ID, DRV_ID, ApplicationStatus.SCHEDULED, LocalDateTime.now().minusDays(2), "Scheduled"));

        // 5. Interviewer & Room
        interviewerRepository.add(new Interviewer(INT_1_ID, "Dr. Stephen Strange", CMP_ID));
        roomRepository.add(new Room(ROOM_1_ID, "Interview Room Alpha", 6));

        // 6. Confirmed Sessions
        LocalDateTime t1 = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0);
        LocalDateTime t2 = t1.plusMinutes(45);
        LocalDateTime t3 = LocalDateTime.now().plusDays(2).withHour(11).withMinute(0);
        LocalDateTime t4 = t3.plusMinutes(45);

        sessionRepository.add(new InterviewSession(SESS_1_ID, APP_1_ID, INT_1_ID, ROOM_1_ID, t1, t2));
        sessionRepository.add(new InterviewSession(SESS_2_ID, APP_2_ID, INT_1_ID, ROOM_1_ID, t3, t4));

        // 7. Security Accounts
        String hash = passwordHasher.hashPassword("Pass2026!".toCharArray());
        userAccountRepository.save(UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username("stu_diana")
                .passwordHash(hash)
                .role(UserRole.STUDENT)
                .studentId(STU_1_ID)
                .displayName("Diana Prince")
                .enabled(true)
                .build());

        userAccountRepository.save(UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username("stu_clark")
                .passwordHash(hash)
                .role(UserRole.STUDENT)
                .studentId(STU_2_ID)
                .displayName("Clark Kent")
                .enabled(true)
                .build());

        userAccountRepository.save(UserAccount.builder()
                .id(UUID.randomUUID().toString())
                .username("off_user")
                .passwordHash(hash)
                .role(UserRole.PLACEMENT_OFFICER)
                .displayName("Officer User")
                .enabled(true)
                .build());

        HireFlowUserPrincipal p1 = new HireFlowUserPrincipal(
                new UserPrincipal("stu_diana", "Diana Prince", UserRole.STUDENT, STU_1_ID)
        );
        studentDianaAuth = new UsernamePasswordAuthenticationToken(p1, null, p1.getAuthorities());

        HireFlowUserPrincipal p2 = new HireFlowUserPrincipal(
                new UserPrincipal("stu_clark", "Clark Kent", UserRole.STUDENT, STU_2_ID)
        );
        studentClarkAuth = new UsernamePasswordAuthenticationToken(p2, null, p2.getAuthorities());

        HireFlowUserPrincipal p3 = new HireFlowUserPrincipal(
                new UserPrincipal("off_user", "Officer User", UserRole.PLACEMENT_OFFICER, null)
        );
        officerAuth = new UsernamePasswordAuthenticationToken(p3, null, p3.getAuthorities());
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM interview_sessions WHERE id LIKE 'SES-%'");
            stmt.executeUpdate("DELETE FROM applications WHERE drive_id = '" + DRV_ID + "' OR id LIKE 'APP-STU-%'");
            stmt.executeUpdate("DELETE FROM interviewers WHERE id = '" + INT_1_ID + "'");
            stmt.executeUpdate("DELETE FROM rooms WHERE id = '" + ROOM_1_ID + "'");
            stmt.executeUpdate("DELETE FROM recruitment_drives WHERE id = '" + DRV_ID + "'");
            stmt.executeUpdate("DELETE FROM companies WHERE id = '" + CMP_ID + "'");
            stmt.executeUpdate("DELETE FROM user_accounts WHERE username IN ('stu_diana', 'stu_clark', 'off_user')");
            stmt.executeUpdate("DELETE FROM students WHERE id IN ('" + STU_1_ID + "', '" + STU_2_ID + "')");
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("GET /api/v1/student/interviews - Success: Student retrieves own confirmed interviews")
    void testGetStudentInterviews_success() throws Exception {
        mockMvc.perform(get("/api/v1/student/interviews")
                        .with(authentication(studentDianaAuth)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sessionId", is(SESS_1_ID)))
                .andExpect(jsonPath("$[0].studentId", is(STU_1_ID)))
                .andExpect(jsonPath("$[0].studentName", is("Diana Prince")))
                .andExpect(jsonPath("$[0].companyName", is("Orion Cybernetics")))
                .andExpect(jsonPath("$[0].jobTitle", is("AI Research Engineer")))
                .andExpect(jsonPath("$[0].interviewerName", is("Dr. Stephen Strange")))
                .andExpect(jsonPath("$[0].roomName", is("Interview Room Alpha")))
                .andExpect(jsonPath("$[0].status", is("SCHEDULED")));
    }

    @Test
    @DisplayName("GET /api/v1/student/interviews - Isolation: Candidate never sees other candidates' interviews")
    void testGetStudentInterviews_isolation() throws Exception {
        // Clark Kent calls getStudentInterviews
        mockMvc.perform(get("/api/v1/student/interviews")
                        .with(authentication(studentClarkAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sessionId", is(SESS_2_ID)))
                .andExpect(jsonPath("$[0].studentId", is(STU_2_ID)))
                .andExpect(jsonPath("$[0].studentName", is("Clark Kent")));
    }

    @Test
    @DisplayName("GET /api/v1/student/interviews/{id} - Success: Student retrieves own interview by ID")
    void testGetStudentInterviewById_success() throws Exception {
        mockMvc.perform(get("/api/v1/student/interviews/" + SESS_1_ID)
                        .with(authentication(studentDianaAuth)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.sessionId", is(SESS_1_ID)))
                .andExpect(jsonPath("$.studentId", is(STU_1_ID)));
    }

    @Test
    @DisplayName("GET /api/v1/student/interviews/{id} - Security: Cross-student interview access returns 404 (non-disclosing)")
    void testGetStudentInterviewById_crossStudent_notFound() throws Exception {
        // Clark Kent attempts to access Diana's interview session
        mockMvc.perform(get("/api/v1/student/interviews/" + SESS_1_ID)
                        .with(authentication(studentClarkAuth)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("was not found")));
    }

    @Test
    @DisplayName("GET /api/v1/student/interviews - Security: Officer role is forbidden from student endpoints")
    void testGetStudentInterviews_forbiddenForOfficer() throws Exception {
        mockMvc.perform(get("/api/v1/student/interviews")
                        .with(authentication(officerAuth)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/student/interviews - Security: Unauthenticated request returns 401")
    void testGetStudentInterviews_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/student/interviews"))
                .andExpect(status().isUnauthorized());
    }
}
