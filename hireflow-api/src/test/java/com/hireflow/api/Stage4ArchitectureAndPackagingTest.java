package com.hireflow.api;

import com.hireflow.api.dto.CompanyDto;
import com.hireflow.api.dto.RecruitmentDriveDto;
import com.hireflow.api.entity.CompanyJpaEntity;
import com.hireflow.api.entity.RecruitmentDriveJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class Stage4ArchitectureAndPackagingTest {

    @Test
    @DisplayName("DTO records are strictly immutable and have no setters")
    void dtoRecordsAreImmutable() {
        assertTrue(CompanyDto.class.isRecord(), "CompanyDto must be an immutable record");
        assertTrue(RecruitmentDriveDto.class.isRecord(), "RecruitmentDriveDto must be an immutable record");
        assertTrue(com.hireflow.api.dto.CsrfTokenDto.class.isRecord(), "CsrfTokenDto must be an immutable record");
        assertTrue(com.hireflow.api.dto.LoginRequestDto.class.isRecord(), "LoginRequestDto must be an immutable record");
        assertTrue(com.hireflow.api.dto.UserProfileDto.class.isRecord(), "UserProfileDto must be an immutable record");
        assertTrue(com.hireflow.api.dto.LogoutResponseDto.class.isRecord(), "LogoutResponseDto must be an immutable record");

        Class<?>[] dts = {
                CompanyDto.class,
                RecruitmentDriveDto.class,
                com.hireflow.api.dto.CsrfTokenDto.class,
                com.hireflow.api.dto.LoginRequestDto.class,
                com.hireflow.api.dto.UserProfileDto.class,
                com.hireflow.api.dto.LogoutResponseDto.class
        };

        for (Class<?> dtoClass : dts) {
            for (Method m : dtoClass.getDeclaredMethods()) {
                assertFalse(m.getName().startsWith("set"), dtoClass.getSimpleName() + " must not have setter methods: " + m.getName());
            }
        }
    }

    @Test
    @DisplayName("Read-only JPA entity mapping converts cleanly to immutable domain models")
    void entityToDomainConversionIntegrity() {
        CompanyJpaEntity company = new CompanyJpaEntity(
                "TEST-COMP-01", "Acme Corporation", "Technology",
                LocalDateTime.now(), LocalDateTime.now()
        );

        RecruitmentDriveJpaEntity drive = new RecruitmentDriveJpaEntity(
                "TEST-DRV-01",
                company,
                "Backend Engineer",
                BigDecimal.valueOf(7.50),
                Set.of("CSE", "IT"),
                LocalDate.of(2027, 6, 30),
                false,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        var domainCompany = company.toDomain();
        assertEquals("TEST-COMP-01", domainCompany.getId());
        assertEquals("Acme Corporation", domainCompany.getName());
        assertEquals("Technology", domainCompany.getIndustry());

        var domainDrive = drive.toDomain();
        assertEquals("TEST-DRV-01", domainDrive.getId());
        assertEquals("TEST-COMP-01", domainDrive.getCompanyId());
        assertEquals("Backend Engineer", domainDrive.getJobTitle());
        assertEquals(7.50, domainDrive.getMinimumCgpa());
        assertTrue(domainDrive.isBranchEligible("CSE"));
        assertTrue(domainDrive.isBranchEligible("IT"));
        assertFalse(domainDrive.isBranchEligible("CIVIL"));
        assertTrue(domainDrive.isCgpaEligible(7.50));
        assertFalse(domainDrive.isCgpaEligible(7.49));
        assertFalse(domainDrive.isClosed());
    }

    @Test
    @DisplayName("Stage 4 API catalog controllers restrict operations to read-only GET methods")
    void controllersOnlyExposeGetMethods() {
        Class<?>[] controllers = {
                com.hireflow.api.controller.HealthController.class,
                com.hireflow.api.controller.CompanyController.class,
                com.hireflow.api.controller.RecruitmentDriveController.class
        };

        for (Class<?> controller : controllers) {
            for (Method method : controller.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers())) {
                    assertFalse(method.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class),
                            controller.getSimpleName() + "." + method.getName() + " must not have @PostMapping");
                    assertFalse(method.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class),
                            controller.getSimpleName() + "." + method.getName() + " must not have @PutMapping");
                    assertFalse(method.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class),
                            controller.getSimpleName() + "." + method.getName() + " must not have @DeleteMapping");
                    assertFalse(method.isAnnotationPresent(org.springframework.web.bind.annotation.PatchMapping.class),
                            controller.getSimpleName() + "." + method.getName() + " must not have @PatchMapping");
                }
            }
        }
    }

    @Test
    @DisplayName("Stage 4B AuthController restricts mutations to POST for login/logout and GET for csrf/me")
    void authControllerMethodBoundaries() {
        Class<?> authController = com.hireflow.api.controller.AuthController.class;
        for (Method method : authController.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                assertFalse(method.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class),
                        "AuthController must not have @PutMapping: " + method.getName());
                assertFalse(method.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class),
                        "AuthController must not have @DeleteMapping: " + method.getName());
                assertFalse(method.isAnnotationPresent(org.springframework.web.bind.annotation.PatchMapping.class),
                        "AuthController must not have @PatchMapping: " + method.getName());
            }
        }
    }
}
