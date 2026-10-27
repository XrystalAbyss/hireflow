package com.hireflow.repository;

import com.hireflow.db.*;
import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.Company;
import com.hireflow.model.Room;
import com.hireflow.model.Student;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Stage 2B.2 Integration Tests for PostgreSQL JDBC Repositories:
 * - {@link JdbcCompanyRepository}
 * - {@link JdbcRoomRepository}
 * - {@link JdbcStudentRepository}
 *
 * Runs strictly against hireflow_test with deterministic ID prefixes ('TEST-').
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JdbcCompanyRoomStudentRepositoryTest {

    private static DatabaseConfig testConfig;
    private static DataSourceProvider dataSourceProvider;
    private static TransactionManager transactionManager;
    private static boolean dbAvailable = false;

    private JdbcCompanyRepository companyRepo;
    private JdbcRoomRepository roomRepo;
    private JdbcStudentRepository studentRepo;

    @BeforeAll
    static void setUpAll() {
        Properties localProps = new Properties();
        File localFile = new File("hireflow-local.properties");
        if (localFile.exists()) {
            try (FileInputStream fis = new FileInputStream(localFile)) {
                localProps.load(fis);
            } catch (Exception ignored) {
            }
        }

        String url = System.getenv().getOrDefault("HIREFLOW_DB_URL",
                System.getProperty("hireflow.db.url", localProps.getProperty("hireflow.db.url", com.hireflow.testsupport.CoreTestDatabase.url())));
        String user = System.getenv().getOrDefault("HIREFLOW_DB_USER",
                System.getProperty("hireflow.db.user", localProps.getProperty("hireflow.db.user", "hireflow_user")));
        String pass = System.getenv().getOrDefault("HIREFLOW_DB_PASSWORD",
                System.getProperty("hireflow.db.password", localProps.getProperty("hireflow.db.password", "")));

        testConfig = DatabaseConfig.builder()
                .mode(DatabaseMode.JDBC_TEST)
                .jdbcUrl(url)
                .username(user)
                .password(pass)
                .maximumPoolSize(5)
                .minimumIdle(1)
                .connectionTimeoutMs(5000)
                .build();

        try {
            dataSourceProvider = new DataSourceProvider(testConfig);
            dbAvailable = dataSourceProvider.verifyConnectionHealth(3);
            if (dbAvailable) {
                FlywayMigrationService migrationService = new FlywayMigrationService(dataSourceProvider.getDataSource());
                migrationService.migrate();
                transactionManager = new TransactionManager(dataSourceProvider.getDataSource(), 3);
            }
        } catch (Exception ex) {
            dbAvailable = false;
        }
    }

    @AfterAll
    static void tearDownAll() {
        if (dbAvailable && dataSourceProvider != null) {
            cleanTestData();
            dataSourceProvider.close();
        }
    }

    @BeforeEach
    void setUp() {
        assumeTrue(dbAvailable, "PostgreSQL test database not available at " + (testConfig != null ? testConfig.getJdbcUrl() : ""));
        companyRepo = new JdbcCompanyRepository(dataSourceProvider.getDataSource());
        roomRepo = new JdbcRoomRepository(dataSourceProvider.getDataSource());
        studentRepo = new JdbcStudentRepository(dataSourceProvider.getDataSource());
        cleanTestData();
    }

    private static void cleanTestData() {
        com.hireflow.testsupport.CoreTestDatabase.cleanCurrentClass();
    }

    // =========================================================================
    // Company Repository Tests
    // =========================================================================

    @Nested
    @DisplayName("Company Repository Operations")
    class CompanyTests {

        @Test
        @DisplayName("Valid add, findById, existsById, and count")
        void testAddAndFindCompany() {
            Company company = new Company("TEST-CMP-01", "Test Company Alpha", "Software");
            Company added = companyRepo.add(company);

            assertNotNull(added);
            assertEquals("TEST-CMP-01", added.getId());
            assertTrue(companyRepo.existsById("TEST-CMP-01"));

            Optional<Company> found = companyRepo.findById("TEST-CMP-01");
            assertTrue(found.isPresent());
            assertEquals("Test Company Alpha", found.get().getName());
            assertEquals("Software", found.get().getIndustry());
        }

        @Test
        @DisplayName("Update company modifies attributes and updates timestamp")
        void testUpdateCompany() throws Exception {
            Company company = new Company("TEST-CMP-02", "Beta Innovations", "Robotics");
            companyRepo.add(company);

            company.setName("Beta AI Systems");
            company.setIndustry("Artificial Intelligence");
            Company updated = companyRepo.update(company);

            assertEquals("Beta AI Systems", updated.getName());
            Optional<Company> found = companyRepo.findById("TEST-CMP-02");
            assertTrue(found.isPresent());
            assertEquals("Beta AI Systems", found.get().getName());
            assertEquals("Artificial Intelligence", found.get().getIndustry());
        }

        @Test
        @DisplayName("Update non-existent company throws EntityNotFoundException")
        void testUpdateNonExistentCompanyThrows() {
            Company nonExistent = new Company("TEST-CMP-99", "Ghost Corp", "Finance");
            EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> companyRepo.update(nonExistent));
            assertTrue(ex.getMessage().contains("was not found"));
        }

        @Test
        @DisplayName("Duplicate ID rejection throws DuplicateEntityException")
        void testDuplicateIdThrows() {
            Company c1 = new Company("TEST-CMP-DUP", "Corp One", "FinTech");
            Company c2 = new Company("TEST-CMP-DUP", "Corp Two", "Health");
            companyRepo.add(c1);

            assertThrows(DuplicateEntityException.class, () -> companyRepo.add(c2));
        }

        @Test
        @DisplayName("Duplicate company name throws DuplicateEntityException (uq_companies_name)")
        void testDuplicateNameThrows() {
            Company c1 = new Company("TEST-CMP-N1", "Identical Name Corp", "FinTech");
            Company c2 = new Company("TEST-CMP-N2", "Identical Name Corp", "Health");
            companyRepo.add(c1);

            assertThrows(DuplicateEntityException.class, () -> companyRepo.add(c2));
        }

        @Test
        @DisplayName("Save inserts when not present and updates when present")
        void testSaveCompanyUpsert() {
            Company c = new Company("TEST-CMP-SAVE", "Save Corp", "Retail");
            companyRepo.save(c);

            Optional<Company> found = companyRepo.findById("TEST-CMP-SAVE");
            assertTrue(found.isPresent());
            assertEquals("Retail", found.get().getIndustry());

            c.setIndustry("E-Commerce");
            companyRepo.save(c);

            Optional<Company> updated = companyRepo.findById("TEST-CMP-SAVE");
            assertTrue(updated.isPresent());
            assertEquals("E-Commerce", updated.get().getIndustry());
        }

        @Test
        @DisplayName("FindByName case-insensitive lookup")
        void testFindByNameCaseInsensitive() {
            companyRepo.add(new Company("TEST-CMP-NAME", "Cyber Security Inc", "Security"));

            Optional<Company> found = companyRepo.findByName("cyber security inc");
            assertTrue(found.isPresent());
            assertEquals("TEST-CMP-NAME", found.get().getId());

            Optional<Company> upper = companyRepo.findByName("CYBER SECURITY INC");
            assertTrue(upper.isPresent());

            assertFalse(companyRepo.findByName("NonExistent").isPresent());
        }

        @Test
        @DisplayName("DeleteById removes company and returns false if non-existent")
        void testDeleteCompany() {
            Company c = new Company("TEST-CMP-DEL", "Delete Me", "Logistics");
            companyRepo.add(c);

            assertTrue(companyRepo.deleteById("TEST-CMP-DEL"));
            assertFalse(companyRepo.deleteById("TEST-CMP-DEL"));
            assertFalse(companyRepo.existsById("TEST-CMP-DEL"));
        }

        @Test
        @DisplayName("Persistence across new repository instance")
        void testPersistenceAcrossInstances() {
            companyRepo.add(new Company("TEST-CMP-PERSIST", "Persistent Corp", "Aerospace"));

            JdbcCompanyRepository freshRepo = new JdbcCompanyRepository(dataSourceProvider.getDataSource());
            Optional<Company> found = freshRepo.findById("TEST-CMP-PERSIST");
            assertTrue(found.isPresent());
            assertEquals("Persistent Corp", found.get().getName());
        }
    }

    // =========================================================================
    // Room Repository Tests
    // =========================================================================

    @Nested
    @DisplayName("Room Repository Operations")
    class RoomTests {

        @Test
        @DisplayName("Valid add, findById, and count")
        void testAddAndFindRoom() {
            Room room = new Room("TEST-RM-01", "Interview Booth 1", 3);
            roomRepo.add(room);

            Optional<Room> found = roomRepo.findById("TEST-RM-01");
            assertTrue(found.isPresent());
            assertEquals("Interview Booth 1", found.get().getName());
            assertEquals(3, found.get().getCapacity());
        }

        @Test
        @DisplayName("Update room modifies capacity and name")
        void testUpdateRoom() {
            Room room = new Room("TEST-RM-02", "Small Cubicle", 2);
            roomRepo.add(room);

            room.setName("Large Conference Cubicle");
            room.setCapacity(6);
            roomRepo.update(room);

            Optional<Room> updated = roomRepo.findById("TEST-RM-02");
            assertTrue(updated.isPresent());
            assertEquals("Large Conference Cubicle", updated.get().getName());
            assertEquals(6, updated.get().getCapacity());
        }

        @Test
        @DisplayName("Update non-existent room throws EntityNotFoundException")
        void testUpdateNonExistentRoomThrows() {
            Room ghost = new Room("TEST-RM-99", "Phantom Room", 4);
            assertThrows(EntityNotFoundException.class, () -> roomRepo.update(ghost));
        }

        @Test
        @DisplayName("Duplicate room ID throws DuplicateEntityException")
        void testDuplicateRoomIdThrows() {
            Room r1 = new Room("TEST-RM-DUP", "Room D1", 2);
            Room r2 = new Room("TEST-RM-DUP", "Room D2", 4);
            roomRepo.add(r1);

            assertThrows(DuplicateEntityException.class, () -> roomRepo.add(r2));
        }

        @Test
        @DisplayName("Duplicate room name throws DuplicateEntityException (uq_rooms_name)")
        void testDuplicateRoomNameThrows() {
            Room r1 = new Room("TEST-RM-N1", "Identical Room Name", 2);
            Room r2 = new Room("TEST-RM-N2", "Identical Room Name", 4);
            roomRepo.add(r1);

            assertThrows(DuplicateEntityException.class, () -> roomRepo.add(r2));
        }

        @Test
        @DisplayName("FindByMinCapacity returns matching rooms in capacity order")
        void testFindByMinCapacity() {
            roomRepo.add(new Room("TEST-RM-C1", "Small Office", 2));
            roomRepo.add(new Room("TEST-RM-C2", "Boardroom", 10));
            roomRepo.add(new Room("TEST-RM-C3", "Auditorium", 50));

            List<Room> largeRooms = roomRepo.findByMinCapacity(10);
            assertEquals(2, largeRooms.size());
            assertEquals("TEST-RM-C2", largeRooms.get(0).getId());
            assertEquals("TEST-RM-C3", largeRooms.get(1).getId());

            assertThrows(UnsupportedOperationException.class, () -> largeRooms.clear());
        }

        @Test
        @DisplayName("FindByName case-insensitive lookup")
        void testFindByNameCaseInsensitive() {
            roomRepo.add(new Room("TEST-RM-NAME", "Lab Complex Alpha", 5));

            Optional<Room> found = roomRepo.findByName("lab complex alpha");
            assertTrue(found.isPresent());
            assertEquals("TEST-RM-NAME", found.get().getId());
        }

        @Test
        @DisplayName("Save room upsert behavior")
        void testSaveRoom() {
            Room r = new Room("TEST-RM-SAVE", "Save Venue", 4);
            roomRepo.save(r);

            assertEquals(4, roomRepo.findById("TEST-RM-SAVE").orElseThrow().getCapacity());

            r.setCapacity(8);
            roomRepo.save(r);

            assertEquals(8, roomRepo.findById("TEST-RM-SAVE").orElseThrow().getCapacity());
        }

        @Test
        @DisplayName("DeleteById removes room")
        void testDeleteRoom() {
            Room r = new Room("TEST-RM-DEL", "Delete Venue", 2);
            roomRepo.add(r);

            assertTrue(roomRepo.deleteById("TEST-RM-DEL"));
            assertFalse(roomRepo.deleteById("TEST-RM-DEL"));
        }
    }

    // =========================================================================
    // Student Repository Tests
    // =========================================================================

    @Nested
    @DisplayName("Student Repository Operations")
    class StudentTests {

        @Test
        @DisplayName("Valid add, findById, and attribute mapping")
        void testAddAndFindStudent() {
            Student s = new Student("TEST-STU-01", "Zara Larson", "zara@univ.edu", "CSE", 8.85, 2026, true);
            Student saved = studentRepo.add(s);

            assertNotNull(saved);
            Optional<Student> found = studentRepo.findById("TEST-STU-01");
            assertTrue(found.isPresent());
            assertEquals("Zara Larson", found.get().getName());
            assertEquals("zara@univ.edu", found.get().getEmail());
            assertEquals("CSE", found.get().getBranch());
            assertEquals(8.85, found.get().getCgpa());
            assertEquals(2026, found.get().getGraduationYear());
            assertTrue(found.get().isActive());
        }

        @Test
        @DisplayName("Update student modifies CGPA, active status and details")
        void testUpdateStudent() {
            Student s = new Student("TEST-STU-02", "Brandon Stark", "brandon@univ.edu", "IT", 7.50, 2026, true);
            studentRepo.add(s);

            s.setCgpa(9.20);
            s.setName("Bran the Broken");
            s.setActive(false);
            studentRepo.update(s);

            Student updated = studentRepo.findById("TEST-STU-02").orElseThrow();
            assertEquals(9.20, updated.getCgpa());
            assertEquals("Bran the Broken", updated.getName());
            assertFalse(updated.isActive());
        }

        @Test
        @DisplayName("Update non-existent student throws EntityNotFoundException")
        void testUpdateNonExistentThrows() {
            Student nonExistent = new Student("TEST-STU-99", "Ghost Student", "ghost@univ.edu", "ECE", 8.0, 2026);
            assertThrows(EntityNotFoundException.class, () -> studentRepo.update(nonExistent));
        }

        @Test
        @DisplayName("Duplicate ID throws DuplicateEntityException")
        void testDuplicateStudentIdThrows() {
            Student s1 = new Student("TEST-STU-DUP", "Student A", "email_a@univ.edu", "CSE", 8.0, 2026);
            Student s2 = new Student("TEST-STU-DUP", "Student B", "email_b@univ.edu", "ECE", 8.5, 2026);
            studentRepo.add(s1);

            assertThrows(DuplicateEntityException.class, () -> studentRepo.add(s2));
        }

        @Test
        @DisplayName("Duplicate email throws DuplicateEntityException (uq_students_email)")
        void testDuplicateStudentEmailThrows() {
            Student s1 = new Student("TEST-STU-E1", "Student A", "shared.email@univ.edu", "CSE", 8.0, 2026);
            Student s2 = new Student("TEST-STU-E2", "Student B", "shared.email@univ.edu", "ECE", 8.5, 2026);
            studentRepo.add(s1);

            assertThrows(DuplicateEntityException.class, () -> studentRepo.add(s2));
        }

        @Test
        @DisplayName("FindByEmail case-insensitive search")
        void testFindByEmailCaseInsensitive() {
            studentRepo.add(new Student("TEST-STU-EM", "Emma Watson", "emma.watson@univ.edu", "CSE", 9.1, 2026));

            Optional<Student> found = studentRepo.findByEmail("EMMA.WATSON@UNIV.EDU");
            assertTrue(found.isPresent());
            assertEquals("TEST-STU-EM", found.get().getId());
        }

        @Test
        @DisplayName("FindByBranch returns matching students ordered by ID")
        void testFindByBranch() {
            studentRepo.add(new Student("TEST-STU-B1", "Student 1", "s1@univ.edu", "AERO", 8.0, 2026));
            studentRepo.add(new Student("TEST-STU-B2", "Student 2", "s2@univ.edu", "AERO", 8.5, 2026));
            studentRepo.add(new Student("TEST-STU-B3", "Student 3", "s3@univ.edu", "MECH", 7.5, 2026));

            List<Student> aeroStudents = studentRepo.findByBranch("aero");
            assertEquals(2, aeroStudents.size());
            assertEquals("TEST-STU-B1", aeroStudents.get(0).getId());
            assertEquals("TEST-STU-B2", aeroStudents.get(1).getId());

            assertThrows(UnsupportedOperationException.class, () -> aeroStudents.clear());
        }

        @Test
        @DisplayName("Save student upsert behavior")
        void testSaveStudent() {
            Student s = new Student("TEST-STU-SAVE", "Save Student", "save.student@univ.edu", "CIVIL", 7.1, 2026);
            studentRepo.save(s);

            assertEquals(7.1, studentRepo.findById("TEST-STU-SAVE").orElseThrow().getCgpa());

            s.setCgpa(8.9);
            studentRepo.save(s);

            assertEquals(8.9, studentRepo.findById("TEST-STU-SAVE").orElseThrow().getCgpa());
        }

        @Test
        @DisplayName("DeleteById removes student")
        void testDeleteStudent() {
            Student s = new Student("TEST-STU-DEL", "Delete Student", "del.student@univ.edu", "CSE", 7.5, 2026);
            studentRepo.add(s);

            assertTrue(studentRepo.deleteById("TEST-STU-DEL"));
            assertFalse(studentRepo.deleteById("TEST-STU-DEL"));
        }
    }

    // =========================================================================
    // Transactional Consistency Tests
    // =========================================================================

    @Nested
    @DisplayName("Transactional Consistency Operations")
    class TransactionTests {

        @Test
        @DisplayName("Atomic multi-repository commit persists all entities")
        void testMultiRepositoryTransactionCommit() {
            transactionManager.runInTransactionWithoutResult(conn -> {
                companyRepo.add(new Company("TEST-CMP-TX1", "Tx Company Corp", "Biotech"));
                roomRepo.add(new Room("TEST-RM-TX1", "Tx Room Alpha", 5));
                studentRepo.add(new Student("TEST-STU-TX1", "Tx Student One", "tx.stu1@univ.edu", "CSE", 8.5, 2026));
            });

            // Verify via independent repository instances
            assertTrue(companyRepo.findById("TEST-CMP-TX1").isPresent());
            assertTrue(roomRepo.findById("TEST-RM-TX1").isPresent());
            assertTrue(studentRepo.findById("TEST-STU-TX1").isPresent());
        }

        @Test
        @DisplayName("Multi-repository rollback upon failure rolls back all entities")
        void testMultiRepositoryTransactionRollback() {
            assertThrows(RuntimeException.class, () -> {
                transactionManager.runInTransactionWithoutResult(conn -> {
                    companyRepo.add(new Company("TEST-CMP-TX2", "Rollback Company", "Energy"));
                    roomRepo.add(new Room("TEST-RM-TX2", "Rollback Room", 10));
                    studentRepo.add(new Student("TEST-STU-TX2", "Rollback Student", "rb.stu2@univ.edu", "IT", 8.0, 2026));

                    // Deliberate mid-transaction failure
                    throw new RuntimeException("Simulated abort in transactional workflow");
                });
            });

            // None of the records must exist
            assertFalse(companyRepo.findById("TEST-CMP-TX2").isPresent());
            assertFalse(roomRepo.findById("TEST-RM-TX2").isPresent());
            assertFalse(studentRepo.findById("TEST-STU-TX2").isPresent());
        }
    }
}
