package com.hireflow.context;

import com.hireflow.db.DatabaseConfig;
import com.hireflow.db.DatabaseMode;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Student;
import com.hireflow.service.SampleDataLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Stage 2B.6: Live hireflow_dev Real Application Restart Demonstration")
public class JdbcDevRestartVerification {

    private DatabaseConfig resolveDevConfig() {
        Properties localProps = new Properties();
        File localFile = new File("hireflow-local.properties");
        if (localFile.exists()) {
            try (FileInputStream fis = new FileInputStream(localFile)) {
                localProps.load(fis);
            } catch (Exception ignored) {
            }
        }

        String user = System.getenv().getOrDefault("HIREFLOW_DB_USER",
                System.getProperty("hireflow.db.user", localProps.getProperty("hireflow.db.user", "hireflow_user")));
        String pass = System.getenv().getOrDefault("HIREFLOW_DB_PASSWORD",
                System.getProperty("hireflow.db.password", localProps.getProperty("hireflow.db.password", "")));

        return DatabaseConfig.builder()
                .mode(DatabaseMode.JDBC_DEV)
                .jdbcUrl(com.hireflow.testsupport.CoreTestDatabase.url())
                .username(user)
                .password(pass)
                .maximumPoolSize(3)
                .minimumIdle(1)
                .connectionTimeoutMs(4000)
                .build();
    }

    @Test
    @DisplayName("Demonstrates persistent state across real application restarts against hireflow_dev without deleting existing records")
    void testRealRestartAgainstHireflowDev() {
        DatabaseConfig devConfig = resolveDevConfig();

        String restartStudentId = "DEV-RESTART-001";
        String restartCompanyId = "DEV-CMP-RESTART";
        String restartDriveId = "DEV-DRV-RESTART";

        // =========================================================================
        // SESSION 1 (First Application Context Launch)
        // =========================================================================
        System.out.println("[*] Session 1: Booting ApplicationContext on hireflow_dev...");
        int initialStudentCount;
        try (ApplicationContext session1 = ApplicationContext.create(devConfig, true)) {
            assertEquals(DatabaseMode.JDBC_DEV, session1.getMode());
            assertTrue(session1.getDataSourceProvider().isPresent());
            assertTrue(session1.getDataSourceProvider().get().verifyConnectionHealth(3));

            initialStudentCount = session1.getStudentService().getTotalStudentsCount();

            // Save records if not already present
            if (!session1.getCompanyRepository().existsById(restartCompanyId)) {
                session1.getCompanyRepository().save(new Company(restartCompanyId, "Persistent Systems Dev", "Enterprise Cloud"));
            }
            if (!session1.getStudentRepository().existsById(restartStudentId)) {
                session1.getStudentRepository().save(new Student(
                        restartStudentId, "Dev Restart Candidate", "dev.restart@univ.edu", "CSE", 8.88, 2026, true
                ));
            }
            if (!session1.getDriveRepository().existsById(restartDriveId)) {
                session1.getDriveRepository().save(new RecruitmentDrive(
                        restartDriveId, restartCompanyId, "Site Reliability Engineer", 8.0, Set.of("CSE", "IT"), LocalDate.now().plusDays(25)
                ));
            }

            assertTrue(session1.getStudentRepository().existsById(restartStudentId));
            assertTrue(session1.getCompanyRepository().existsById(restartCompanyId));
            assertTrue(session1.getDriveRepository().existsById(restartDriveId));

            System.out.println("    Session 1 written & verified records in hireflow_dev.");
            System.out.println("    Shutting down Session 1 pool...");
        }

        // =========================================================================
        // SESSION 2 (Simulated Application Restart: Brand New ApplicationContext)
        // =========================================================================
        System.out.println("[*] Session 2: Relaunching ApplicationContext after shutdown...");
        try (ApplicationContext session2 = ApplicationContext.create(devConfig, true)) {
            assertEquals(DatabaseMode.JDBC_DEV, session2.getMode());

            // 1. Verify records written in Session 1 exist in Session 2
            Optional<Student> studentOpt = session2.getStudentRepository().findById(restartStudentId);
            assertTrue(studentOpt.isPresent(), "Student record must survive restart in hireflow_dev");
            assertEquals("Dev Restart Candidate", studentOpt.get().getName());
            assertEquals(8.88, studentOpt.get().getCgpa(), 0.001);

            Optional<Company> compOpt = session2.getCompanyRepository().findById(restartCompanyId);
            assertTrue(compOpt.isPresent(), "Company record must survive restart in hireflow_dev");
            assertEquals("Persistent Systems Dev", compOpt.get().getName());

            Optional<RecruitmentDrive> driveOpt = session2.getDriveRepository().findById(restartDriveId);
            assertTrue(driveOpt.isPresent(), "Drive record must survive restart in hireflow_dev");
            assertEquals("Site Reliability Engineer", driveOpt.get().getJobTitle());

            // 2. Verify SampleDataLoader does not attempt duplicate key seeding
            SampleDataLoader seeder = new SampleDataLoader(
                    session2.getStudentRepository(),
                    session2.getCompanyRepository(),
                    session2.getDriveRepository(),
                    session2.getInterviewerRepository(),
                    session2.getRoomRepository()
            );
            boolean didSeed = seeder.seedIfEmpty();
            assertFalse(didSeed, "seedIfEmpty must skip seeding on populated hireflow_dev database");

            System.out.println("    Session 2 restart verified! All records intact in hireflow_dev.");
        }
    }
}
