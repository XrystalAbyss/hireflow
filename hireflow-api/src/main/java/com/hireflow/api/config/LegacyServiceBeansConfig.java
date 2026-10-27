package com.hireflow.api.config;

import com.hireflow.db.TransactionManager;
import com.hireflow.repository.ApplicationRepository;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.repository.DriveRepository;
import com.hireflow.repository.JdbcApplicationRepository;
import com.hireflow.repository.JdbcCompanyRepository;
import com.hireflow.repository.JdbcDriveRepository;
import com.hireflow.repository.StudentRepository;
import com.hireflow.service.ApplicationService;
import com.hireflow.service.EligibilityService;
import com.hireflow.service.RecruitmentDriveService;
import com.hireflow.service.StudentService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Spring configuration exposing proven legacy domain services and JDBC repositories
 * directly wired to the primary Spring pooled DataSource and HireFlow TransactionManager.
 * Zero duplicate business rules, zero duplicate eligibility formulas, zero mixed JPA/JDBC write contexts.
 */
@Configuration
public class LegacyServiceBeansConfig {

    @Bean(name = "hireflowJdbcTransactionManager")
    public TransactionManager hireflowJdbcTransactionManager(DataSource dataSource) {
        return new TransactionManager(dataSource);
    }

    @Bean
    public ApplicationRepository applicationRepository(
            @Qualifier("hireflowJdbcTransactionManager") TransactionManager transactionManager) {
        return new JdbcApplicationRepository(transactionManager);
    }

    @Bean
    public DriveRepository driveRepository(
            @Qualifier("hireflowJdbcTransactionManager") TransactionManager transactionManager) {
        return new JdbcDriveRepository(transactionManager);
    }

    @Bean
    public CompanyRepository companyRepository(
            @Qualifier("hireflowJdbcTransactionManager") TransactionManager transactionManager) {
        return new JdbcCompanyRepository(transactionManager);
    }


    @Bean
    public ApplicationService applicationService(
            ApplicationRepository applicationRepository,
            StudentRepository studentRepository,
            DriveRepository driveRepository,
            EligibilityService eligibilityService,
            @Qualifier("hireflowJdbcTransactionManager") TransactionManager transactionManager) {
        return new ApplicationService(applicationRepository, studentRepository, driveRepository, eligibilityService, transactionManager);
    }

    @Bean
    public StudentService studentService(
            StudentRepository studentRepository,
            ApplicationRepository applicationRepository) {
        return new StudentService(studentRepository, applicationRepository);
    }

    @Bean
    public com.hireflow.repository.InterviewerRepository interviewerRepository(
            @Qualifier("hireflowJdbcTransactionManager") TransactionManager transactionManager) {
        return new com.hireflow.repository.JdbcInterviewerRepository(transactionManager);
    }

    @Bean
    public com.hireflow.repository.RoomRepository roomRepository(
            @Qualifier("hireflowJdbcTransactionManager") TransactionManager transactionManager) {
        return new com.hireflow.repository.JdbcRoomRepository(transactionManager);
    }

    @Bean
    public com.hireflow.service.CompanyService companyService(
            CompanyRepository companyRepository,
            DriveRepository driveRepository,
            com.hireflow.repository.InterviewerRepository interviewerRepository) {
        return new com.hireflow.service.CompanyService(companyRepository, driveRepository, interviewerRepository);
    }

    @Bean
    public RecruitmentDriveService recruitmentDriveService(
            DriveRepository driveRepository,
            CompanyRepository companyRepository,
            ApplicationRepository applicationRepository) {
        return new RecruitmentDriveService(driveRepository, companyRepository, applicationRepository);
    }

    @Bean
    public com.hireflow.repository.InterviewSessionRepository interviewSessionRepository(
            @Qualifier("hireflowJdbcTransactionManager") TransactionManager transactionManager) {
        return new com.hireflow.repository.JdbcInterviewSessionRepository(transactionManager);
    }

    @Bean
    public com.hireflow.scheduler.ConflictChecker conflictChecker() {
        return new com.hireflow.scheduler.ConflictChecker();
    }

    @Bean
    public com.hireflow.scheduler.SchedulingEngine schedulingEngine(com.hireflow.scheduler.ConflictChecker conflictChecker) {
        return new com.hireflow.scheduler.GreedyScheduler(conflictChecker);
    }

    @Bean
    public com.hireflow.service.InterviewService interviewService(
            com.hireflow.repository.InterviewSessionRepository interviewSessionRepository,
            ApplicationRepository applicationRepository,
            ApplicationService applicationService,
            com.hireflow.scheduler.ConflictChecker conflictChecker,
            @Qualifier("hireflowJdbcTransactionManager") TransactionManager transactionManager) {
        return new com.hireflow.service.InterviewService(
                interviewSessionRepository, applicationRepository, applicationService, conflictChecker, transactionManager);
    }
}
