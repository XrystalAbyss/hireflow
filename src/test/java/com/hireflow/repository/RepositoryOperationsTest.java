package com.hireflow.repository;

import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("In-Memory Repository Operations Tests")
public class RepositoryOperationsTest {

    private StudentRepository studentRepo;
    private CompanyRepository companyRepo;
    private DriveRepository driveRepo;
    private ApplicationRepository applicationRepo;
    private InterviewerRepository interviewerRepo;
    private RoomRepository roomRepo;
    private InterviewSessionRepository sessionRepo;

    @BeforeEach
    void setUp() {
        studentRepo = new InMemoryStudentRepository();
        companyRepo = new InMemoryCompanyRepository();
        driveRepo = new InMemoryDriveRepository();
        applicationRepo = new InMemoryApplicationRepository();
        interviewerRepo = new InMemoryInterviewerRepository();
        roomRepo = new InMemoryRoomRepository();
        sessionRepo = new InMemoryInterviewSessionRepository();
    }

    @Nested
    @DisplayName("StudentRepository Tests")
    class StudentRepoTests {

        @Test
        @DisplayName("Add student increments count and permits retrieval")
        void testAddStudentSuccess() {
            Student student = new Student("STU-001", "Alice", "alice@example.com", "CSE", 8.5, 2026);
            Student saved = studentRepo.add(student);

            assertNotNull(saved);
            assertEquals(1, studentRepo.count());
            assertTrue(studentRepo.existsById("STU-001"));

            Optional<Student> found = studentRepo.findById("STU-001");
            assertTrue(found.isPresent());
            assertEquals("Alice", found.get().getName());
        }

        @Test
        @DisplayName("Add student with duplicate ID throws DuplicateEntityException")
        void testAddDuplicateIdThrows() {
            Student s1 = new Student("STU-001", "Alice", "alice@example.com", "CSE", 8.5, 2026);
            Student s2 = new Student("STU-001", "Bob", "bob@example.com", "ECE", 9.0, 2026);

            studentRepo.add(s1);
            assertThrows(DuplicateEntityException.class, () -> studentRepo.add(s2));
            assertEquals(1, studentRepo.count());
        }

        @Test
        @DisplayName("Add student with duplicate email throws DuplicateEntityException")
        void testAddDuplicateEmailThrows() {
            Student s1 = new Student("STU-001", "Alice", "shared@example.com", "CSE", 8.5, 2026);
            Student s2 = new Student("STU-002", "Bob", "shared@example.com", "ECE", 9.0, 2026);

            studentRepo.add(s1);
            assertThrows(DuplicateEntityException.class, () -> studentRepo.add(s2));
        }

        @Test
        @DisplayName("Add null student throws ValidationException")
        void testAddNullThrows() {
            assertThrows(ValidationException.class, () -> studentRepo.add(null));
        }

        @Test
        @DisplayName("Find by email is case-insensitive")
        void testFindByEmail() {
            Student student = new Student("STU-001", "Alice", "Alice@Example.COM", "CSE", 8.5, 2026);
            studentRepo.add(student);

            Optional<Student> found = studentRepo.findByEmail("alice@example.com");
            assertTrue(found.isPresent());
            assertEquals("STU-001", found.get().getId());
        }

        @Test
        @DisplayName("Find by branch returns matching students and prevents list mutation")
        void testFindByBranchAndImmutability() {
            studentRepo.add(new Student("STU-001", "Alice", "alice@example.com", "CSE", 8.5, 2026));
            studentRepo.add(new Student("STU-002", "Bob", "bob@example.com", "CSE", 7.8, 2026));
            studentRepo.add(new Student("STU-003", "Charlie", "charlie@example.com", "ECE", 9.2, 2026));

            List<Student> cseStudents = studentRepo.findByBranch("cse");
            assertEquals(2, cseStudents.size());

            // Verify unmodifiable collection
            assertThrows(UnsupportedOperationException.class, () -> cseStudents.add(
                    new Student("STU-004", "Dave", "dave@example.com", "CSE", 8.0, 2026)
            ));
        }

        @Test
        @DisplayName("Update student modifies existing record")
        void testUpdateStudentSuccess() {
            Student student = new Student("STU-001", "Alice", "alice@example.com", "CSE", 8.5, 2026);
            studentRepo.add(student);

            student.setCgpa(9.1);
            student.setName("Alice Cooper");
            studentRepo.update(student);

            Student updated = studentRepo.findById("STU-001").orElseThrow();
            assertEquals(9.1, updated.getCgpa());
            assertEquals("Alice Cooper", updated.getName());
        }

        @Test
        @DisplayName("Update student with non-existent ID throws EntityNotFoundException")
        void testUpdateNonExistentThrows() {
            Student student = new Student("STU-999", "Ghost", "ghost@example.com", "CSE", 8.0, 2026);
            assertThrows(EntityNotFoundException.class, () -> studentRepo.update(student));
        }

        @Test
        @DisplayName("Delete by ID removes record and clears repository")
        void testDeleteAndClear() {
            studentRepo.add(new Student("STU-001", "Alice", "alice@example.com", "CSE", 8.5, 2026));
            assertEquals(1, studentRepo.count());

            assertTrue(studentRepo.deleteById("STU-001"));
            assertFalse(studentRepo.deleteById("STU-001"));
            assertEquals(0, studentRepo.count());

            studentRepo.add(new Student("STU-002", "Bob", "bob@example.com", "CSE", 8.5, 2026));
            studentRepo.clear();
            assertEquals(0, studentRepo.count());
        }

        @Test
        @DisplayName("Find all returns unmodifiable collection")
        void testFindAllUnmodifiable() {
            studentRepo.add(new Student("STU-001", "Alice", "alice@example.com", "CSE", 8.5, 2026));
            List<Student> all = studentRepo.findAll();
            assertEquals(1, all.size());

            assertThrows(UnsupportedOperationException.class, () -> all.clear());
        }
    }

    @Nested
    @DisplayName("CompanyRepository Tests")
    class CompanyRepoTests {

        @Test
        @DisplayName("Add, find by name, duplicate ID rejection")
        void testCompanyOperations() {
            Company c1 = new Company("CMP-001", "Google", "Tech");
            companyRepo.add(c1);

            assertEquals(1, companyRepo.count());
            assertTrue(companyRepo.findByName("Google").isPresent());
            assertFalse(companyRepo.findByName("Apple").isPresent());

            Company duplicate = new Company("CMP-001", "Other", "Other");
            assertThrows(DuplicateEntityException.class, () -> companyRepo.add(duplicate));
        }
    }

    @Nested
    @DisplayName("DriveRepository Tests")
    class DriveRepoTests {

        @Test
        @DisplayName("Drive filtering by company and active deadline")
        void testDriveOperations() {
            LocalDate future = LocalDate.now().plusDays(5);
            LocalDate past = LocalDate.now().minusDays(1);

            RecruitmentDrive d1 = new RecruitmentDrive("DRV-001", "CMP-001", "SDE", 8.0, Set.of("CSE"), future);
            RecruitmentDrive d2 = new RecruitmentDrive("DRV-002", "CMP-001", "QA", 7.0, Set.of("CSE", "IT"), past);

            driveRepo.add(d1);
            driveRepo.add(d2);

            assertEquals(2, driveRepo.count());
            assertEquals(2, driveRepo.findByCompanyId("CMP-001").size());

            List<RecruitmentDrive> activeDrives = driveRepo.findActiveDrives(LocalDate.now());
            assertEquals(1, activeDrives.size());
            assertEquals("DRV-001", activeDrives.get(0).getId());
        }
    }

    @Nested
    @DisplayName("ApplicationRepository Tests")
    class ApplicationRepoTests {

        @Test
        @DisplayName("Application add, status query and duplicate application rejection")
        void testApplicationOperations() {
            Application a1 = new Application("APP-001", "STU-001", "DRV-001", ApplicationStatus.APPLIED);
            applicationRepo.add(a1);

            assertEquals(1, applicationRepo.count());
            assertEquals(1, applicationRepo.findByStudentId("STU-001").size());
            assertEquals(1, applicationRepo.findByDriveId("DRV-001").size());
            assertEquals(1, applicationRepo.findByStatus(ApplicationStatus.APPLIED).size());
            assertEquals(0, applicationRepo.findByStatus(ApplicationStatus.SHORTLISTED).size());

            // Duplicate student applying to same drive rejected
            Application a2 = new Application("APP-002", "STU-001", "DRV-001", ApplicationStatus.APPLIED);
            assertThrows(DuplicateEntityException.class, () -> applicationRepo.add(a2));

            // Different student applying to same drive accepted
            Application a3 = new Application("APP-003", "STU-002", "DRV-001", ApplicationStatus.APPLIED);
            assertDoesNotThrow(() -> applicationRepo.add(a3));
        }
    }

    @Nested
    @DisplayName("Interviewer, Room and Session Repository Tests")
    class InterviewAndRoomRepoTests {

        @Test
        @DisplayName("Room repository capacity query and duplicate handling")
        void testRoomRepo() {
            roomRepo.add(new Room("RM-101", "Small Booth", 2));
            roomRepo.add(new Room("RM-102", "Conference A", 10));

            assertEquals(2, roomRepo.count());
            assertTrue(roomRepo.findByName("Small Booth").isPresent());
            assertEquals(1, roomRepo.findByMinCapacity(5).size());
            assertEquals("RM-102", roomRepo.findByMinCapacity(5).get(0).getId());
        }

        @Test
        @DisplayName("Interviewer repository company filtering")
        void testInterviewerRepo() {
            interviewerRepo.add(new Interviewer("INT-001", "John", "CMP-001"));
            interviewerRepo.add(new Interviewer("INT-002", "Jane", "CMP-002"));

            assertEquals(1, interviewerRepo.findByCompanyId("CMP-001").size());
        }

        @Test
        @DisplayName("InterviewSession repository queries by app, interviewer, and room")
        void testSessionRepo() {
            LocalDateTime now = LocalDateTime.now();
            InterviewSession s1 = new InterviewSession("SES-001", "APP-001", "INT-001", "RM-101", now, now.plusHours(1));
            sessionRepo.add(s1);

            assertEquals(1, sessionRepo.count());
            assertEquals(1, sessionRepo.findByApplicationId("APP-001").size());
            assertEquals(1, sessionRepo.findByInterviewerId("INT-001").size());
            assertEquals(1, sessionRepo.findByRoomId("RM-101").size());

            // Duplicate session ID rejected
            InterviewSession sDuplicate = new InterviewSession("SES-001", "APP-002", "INT-002", "RM-102", now, now.plusHours(1));
            assertThrows(DuplicateEntityException.class, () -> sessionRepo.add(sDuplicate));
        }
    }
}
