package com.hireflow.api.config;

import com.hireflow.db.TransactionManager;
import com.hireflow.repository.JdbcStudentRepository;
import com.hireflow.repository.StudentRepository;
import com.hireflow.web.security.AuthenticationService;
import com.hireflow.web.security.BruteForceThrottler;
import com.hireflow.web.security.JdbcUserAccountRepository;
import com.hireflow.web.security.PasswordHasher;
import com.hireflow.web.security.UserAccountRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Spring configuration bridging legacy Stage 3 authentication services into the Spring IoC container.
 * Zero duplicated password hashing, zero duplicate user tables, and zero re-implementation of BCrypt logic.
 */
@Configuration
public class LegacySecurityBeansConfig {

    @Bean
    public UserAccountRepository userAccountRepository(DataSource dataSource) {
        return new JdbcUserAccountRepository(dataSource);
    }

    @Bean
    public StudentRepository studentRepository(
            DataSource dataSource,
            @Qualifier("hireflowJdbcTransactionManager") TransactionManager transactionManager) {
        return new JdbcStudentRepository(transactionManager);
    }

    @Bean
    public PasswordHasher passwordHasher() {
        return new PasswordHasher();
    }

    @Bean
    public BruteForceThrottler bruteForceThrottler() {
        return new BruteForceThrottler();
    }

    @Bean
    public AuthenticationService authenticationService(
            UserAccountRepository userAccountRepository,
            PasswordHasher passwordHasher,
            StudentRepository studentRepository,
            BruteForceThrottler bruteForceThrottler) {
        return new AuthenticationService(userAccountRepository, passwordHasher, studentRepository, bruteForceThrottler);
    }
}
