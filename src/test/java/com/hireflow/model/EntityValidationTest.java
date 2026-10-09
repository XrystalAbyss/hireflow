package com.hireflow.model;

import com.hireflow.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Domain Entity Validation and Encapsulation Tests")
public class EntityValidationTest {

    @Nested
    @DisplayName("Student Entity Tests")
    class StudentTests {

        @Test
        @DisplayName("Valid student creation passes with normalized values")
        void testValidStudentCreation() {
            Student s = new Student("stu-001", "Alice Smith", "Alice@Example.com", "cse", 8.75, 2026);
            assertEquals("STU-001", s.getId());
            assertEquals("Alice Smith", s.getName());
            assertEquals("alice@example.com", s.getEmail());
            assertEquals("CSE", s.getBranch());
            assertEquals(8.75, s.getCgpa());
            assertEquals(2026, s.getGraduationYear());
            assertTrue(s.isActive());
        }

        @Test
        @DisplayName("Blank or null ID throws ValidationException")
        void testBlankStudentId() {
            assertThrows(ValidationException.class, () -> new Student("", "Alice", "alice@example.com", "CSE", 8.0, 2026));
            assertThrows(ValidationException.class, () -> new Student("   ", "Alice", "alice@example.com", "CSE", 8.0, 2026));
            assertThrows(ValidationException.class, () -> new Student(null, "Alice", "alice@example.com", "CSE", 8.0, 2026));
        }

        @Test
        @DisplayName("Short or blank name throws ValidationException")
        void testInvalidStudentName() {
            assertThrows(ValidationException.class, () -> new Student("STU-001", "A", "alice@example.com", "CSE", 8.0, 2026));
            assertThrows(ValidationException.class, () -> new Student("STU-001", "   ", "alice@example.com", "CSE", 8.0, 2026));
            assertThrows(ValidationException.class, () -> new Student("STU-001", null, "alice@example.com", "CSE", 8.0, 2026));
        }

        @Test
        @DisplayName("Malformed email throws ValidationException")
        void testMalformedStudentEmail() {
            assertThrows(ValidationException.class, () -> new Student("STU-001", "Alice", "notanemail", "CSE", 8.0, 2026));
            assertThrows(ValidationException.class, () -> new Student("STU-001", "Alice", "test@missingdot", "CSE", 8.0, 2026));
            assertThrows(ValidationException.class, () -> new Student("STU-001", "Alice", null, "CSE", 8.0, 2026));
        }

        @Test
        @DisplayName("CGPA out of range throws ValidationException")
        void testInvalidCgpa() {
            assertThrows(ValidationException.class, () -> new Student("STU-001", "Alice", "alice@example.com", "CSE", -0.1, 2026));
            assertThrows(ValidationException.class, () -> new Student("STU-001", "Alice", "alice@example.com", "CSE", 10.05, 2026));
        }

        @Test
        @DisplayName("Invalid graduation year throws ValidationException")
        void testInvalidGraduationYear() {
            assertThrows(ValidationException.class, () -> new Student("STU-001", "Alice", "alice@example.com", "CSE", 8.0, 1999));
            assertThrows(ValidationException.class, () -> new Student("STU-001", "Alice", "alice@example.com", "CSE", 8.0, 2150));
        }

        @Test
        @DisplayName("Student equality is based strictly on ID")
        void testStudentEquality() {
            Student s1 = new Student("STU-001", "Alice", "alice@example.com", "CSE", 8.5, 2026);
            Student s2 = new Student("stu-001", "Alice Different", "alice2@example.com", "ECE", 9.0, 2025);
            Student s3 = new Student("STU-002", "Alice", "alice@example.com", "CSE", 8.5, 2026);

            assertEquals(s1, s2);
            assertEquals(s1.hashCode(), s2.hashCode());
            assertNotEquals(s1, s3);
        }
    }

    @Nested
    @DisplayName("Company Entity Tests")
    class CompanyTests {

        @Test
        @DisplayName("Valid company creation and mutation")
        void testValidCompany() {
            Company c = new Company("cmp-001", "Google", "Technology");
            assertEquals("CMP-001", c.getId());
            assertEquals("Google", c.getName());
            assertEquals("Technology", c.getIndustry());

            c.setName("Alphabet / Google");
            assertEquals("Alphabet / Google", c.getName());
        }

        @Test
        @DisplayName("Blank company fields throw ValidationException")
        void testInvalidCompanyFields() {
            assertThrows(ValidationException.class, () -> new Company(null, "Google", "Tech"));
            assertThrows(ValidationException.class, () -> new Company("CMP-001", "  ", "Tech"));
            assertThrows(ValidationException.class, () -> new Company("CMP-001", "Google", ""));
        }

        @Test
        @DisplayName("Company equality based on ID")
        void testCompanyEquality() {
            Company c1 = new Company("CMP-001", "Google", "Tech");
            Company c2 = new Company("cmp-001", "Other", "Other");
            assertEquals(c1, c2);
        }
    }

    @Nested
    @DisplayName("RecruitmentDrive Entity Tests")
    class RecruitmentDriveTests {

        @Test
        @DisplayName("Valid drive creation and branch filtering")
        void testValidRecruitmentDrive() {
            Set<String> branches = new HashSet<>();
            branches.add("CSE");
            branches.add("IT");

            LocalDate deadline = LocalDate.now().plusDays(10);
            RecruitmentDrive drive = new RecruitmentDrive("drv-001", "cmp-001", "Software Engineer", 7.5, branches, deadline);

            assertEquals("DRV-001", drive.getId());
            assertEquals("CMP-001", drive.getCompanyId());
            assertEquals("Software Engineer", drive.getJobTitle());
            assertEquals(7.5, drive.getMinimumCgpa());
            assertEquals(deadline, drive.getApplicationDeadline());

            assertTrue(drive.isBranchEligible("cse"));
            assertTrue(drive.isBranchEligible("IT"));
            assertFalse(drive.isBranchEligible("MECH"));

            assertTrue(drive.isCgpaEligible(7.5));
            assertTrue(drive.isCgpaEligible(8.0));
            assertFalse(drive.isCgpaEligible(7.49));
            assertFalse(drive.isDeadlinePassed(LocalDate.now()));
        }

        @Test
        @DisplayName("Eligible branches prevents accidental exposure of mutable collection")
        void testPreventCollectionExposure() {
            Set<String> branches = new HashSet<>();
            branches.add("CSE");

            RecruitmentDrive drive = new RecruitmentDrive("DRV-001", "CMP-001", "Dev", 7.0, branches, LocalDate.now());

            // 1. Modifying source set should NOT affect internal drive set
            branches.add("CIVIL");
            assertFalse(drive.getEligibleBranches().contains("CIVIL"));

            // 2. Modifying returned set directly should throw UnsupportedOperationException
            Set<String> returnedSet = drive.getEligibleBranches();
            assertThrows(UnsupportedOperationException.class, () -> returnedSet.add("MECH"));
        }

        @Test
        @DisplayName("Invalid drive attributes throw ValidationException")
        void testInvalidDriveAttributes() {
            assertThrows(ValidationException.class, () -> new RecruitmentDrive(null, "C1", "Role", 7.0, Set.of("CSE"), LocalDate.now()));
            assertThrows(ValidationException.class, () -> new RecruitmentDrive("D1", "", "Role", 7.0, Set.of("CSE"), LocalDate.now()));
            assertThrows(ValidationException.class, () -> new RecruitmentDrive("D1", "C1", "  ", 7.0, Set.of("CSE"), LocalDate.now()));
            assertThrows(ValidationException.class, () -> new RecruitmentDrive("D1", "C1", "Role", -1.0, Set.of("CSE"), LocalDate.now()));
            assertThrows(ValidationException.class, () -> new RecruitmentDrive("D1", "C1", "Role", 10.5, Set.of("CSE"), LocalDate.now()));
            assertThrows(ValidationException.class, () -> new RecruitmentDrive("D1", "C1", "Role", 7.0, Set.of("CSE"), null));
        }
    }

    @Nested
    @DisplayName("Application Entity Tests")
    class ApplicationTests {

        @Test
        @DisplayName("Valid application creation and lifecycle update")
        void testValidApplication() {
            Application app = new Application("app-001", "stu-001", "drv-001", ApplicationStatus.APPLIED);
            assertEquals("APP-001", app.getId());
            assertEquals("STU-001", app.getStudentId());
            assertEquals("DRV-001", app.getDriveId());
            assertEquals(ApplicationStatus.APPLIED, app.getStatus());

            app.setStatus(ApplicationStatus.SHORTLISTED);
            assertEquals(ApplicationStatus.SHORTLISTED, app.getStatus());
        }

        @Test
        @DisplayName("Invalid application fields throw ValidationException")
        void testInvalidApplication() {
            assertThrows(ValidationException.class, () -> new Application(null, "S1", "D1", ApplicationStatus.APPLIED));
            assertThrows(ValidationException.class, () -> new Application("A1", "  ", "D1", ApplicationStatus.APPLIED));
            assertThrows(ValidationException.class, () -> new Application("A1", "S1", "", ApplicationStatus.APPLIED));
            assertThrows(ValidationException.class, () -> new Application("A1", "S1", "D1", null));
        }
    }

    @Nested
    @DisplayName("Interviewer and Room Entity Tests")
    class InterviewerAndRoomTests {

        @Test
        @DisplayName("Valid interviewer creation")
        void testValidInterviewer() {
            Interviewer i = new Interviewer("int-001", "Dr. Alan Turing", "cmp-001");
            assertEquals("INT-001", i.getId());
            assertEquals("Dr. Alan Turing", i.getName());
            assertEquals("CMP-001", i.getCompanyId());
        }

        @Test
        @DisplayName("Invalid interviewer attributes throw ValidationException")
        void testInvalidInterviewer() {
            assertThrows(ValidationException.class, () -> new Interviewer(null, "Alan", "CMP-001"));
            assertThrows(ValidationException.class, () -> new Interviewer("INT-001", "  ", "CMP-001"));
            assertThrows(ValidationException.class, () -> new Interviewer("INT-001", "Alan", ""));
        }

        @Test
        @DisplayName("Valid room creation and capacity verification")
        void testValidRoom() {
            Room r = new Room("rm-101", "Interview Booth 1", 3);
            assertEquals("RM-101", r.getId());
            assertEquals("Interview Booth 1", r.getName());
            assertEquals(3, r.getCapacity());

            r.setCapacity(5);
            assertEquals(5, r.getCapacity());
        }

        @Test
        @DisplayName("Non-positive room capacity throws ValidationException")
        void testInvalidRoomCapacity() {
            assertThrows(ValidationException.class, () -> new Room("RM-101", "Booth", 0));
            assertThrows(ValidationException.class, () -> new Room("RM-101", "Booth", -2));
            assertThrows(ValidationException.class, () -> new Room("RM-101", "   ", 4));
        }
    }

    @Nested
    @DisplayName("InterviewSession Entity Tests")
    class InterviewSessionTests {

        @Test
        @DisplayName("Valid session creation and duration calculation")
        void testValidSession() {
            LocalDateTime start = LocalDateTime.of(2026, 10, 15, 10, 0);
            LocalDateTime end = LocalDateTime.of(2026, 10, 15, 11, 0);

            InterviewSession s = new InterviewSession("ses-001", "app-001", "int-001", "rm-101", start, end);
            assertEquals("SES-001", s.getId());
            assertEquals("APP-001", s.getApplicationId());
            assertEquals("INT-001", s.getInterviewerId());
            assertEquals("RM-101", s.getRoomId());
            assertEquals(60, s.getDurationMinutes());
        }

        @Test
        @DisplayName("End time before or equal to start time throws ValidationException")
        void testInvalidTimeInterval() {
            LocalDateTime start = LocalDateTime.of(2026, 10, 15, 11, 0);
            LocalDateTime end = LocalDateTime.of(2026, 10, 15, 10, 0);

            assertThrows(ValidationException.class, () -> new InterviewSession("SES-001", "A1", "I1", "R1", start, end));
            assertThrows(ValidationException.class, () -> new InterviewSession("SES-001", "A1", "I1", "R1", start, start));
            assertThrows(ValidationException.class, () -> new InterviewSession("SES-001", "A1", "I1", "R1", null, end));
        }

        @Test
        @DisplayName("Overlapping intervals correctly evaluated")
        void testIntervalOverlapLogic() {
            LocalDateTime s1 = LocalDateTime.of(2026, 10, 15, 10, 0);
            LocalDateTime e1 = LocalDateTime.of(2026, 10, 15, 11, 0);
            InterviewSession session = new InterviewSession("SES-001", "A1", "I1", "R1", s1, e1);

            // Overlapping
            assertTrue(session.overlaps(
                    LocalDateTime.of(2026, 10, 15, 10, 30),
                    LocalDateTime.of(2026, 10, 15, 11, 30)
            ));

            // Completely enclosed
            assertTrue(session.overlaps(
                    LocalDateTime.of(2026, 10, 15, 10, 15),
                    LocalDateTime.of(2026, 10, 15, 10, 45)
            ));

            // Adjacent touching boundary is NOT an overlap
            assertFalse(session.overlaps(
                    LocalDateTime.of(2026, 10, 15, 11, 0),
                    LocalDateTime.of(2026, 10, 15, 12, 0)
            ));

            assertFalse(session.overlaps(
                    LocalDateTime.of(2026, 10, 15, 9, 0),
                    LocalDateTime.of(2026, 10, 15, 10, 0)
            ));
        }
    }
}
