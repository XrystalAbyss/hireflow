package com.hireflow.api;

import com.hireflow.api.config.LegacyServiceBridgeConfig;
import com.hireflow.api.controller.CompanyController;
import com.hireflow.api.controller.HealthController;
import com.hireflow.api.controller.RecruitmentDriveController;
import com.hireflow.api.repository.CompanyJpaRepository;
import com.hireflow.api.repository.RecruitmentDriveJpaRepository;
import com.hireflow.api.service.CompanyCatalogService;
import com.hireflow.api.service.RecruitmentDriveCatalogService;
import com.hireflow.service.EligibilityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class HireFlowApiApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private CompanyCatalogService companyCatalogService;

    @Autowired
    private RecruitmentDriveCatalogService recruitmentDriveCatalogService;

    @Autowired
    private CompanyJpaRepository companyJpaRepository;

    @Autowired
    private RecruitmentDriveJpaRepository recruitmentDriveJpaRepository;

    @Autowired
    private HealthController healthController;

    @Autowired
    private CompanyController companyController;

    @Autowired
    private RecruitmentDriveController recruitmentDriveController;

    @Autowired
    private EligibilityService eligibilityService;

    @Test
    @DisplayName("Spring Boot context successfully initializes all Stage 4A components")
    void contextLoads() {
        assertNotNull(applicationContext, "Spring ApplicationContext must not be null");
        assertNotNull(companyCatalogService, "CompanyCatalogService bean must be loaded");
        assertNotNull(recruitmentDriveCatalogService, "RecruitmentDriveCatalogService bean must be loaded");
        assertNotNull(companyJpaRepository, "CompanyJpaRepository bean must be loaded");
        assertNotNull(recruitmentDriveJpaRepository, "RecruitmentDriveJpaRepository bean must be loaded");
        assertNotNull(healthController, "HealthController bean must be loaded");
        assertNotNull(companyController, "CompanyController bean must be loaded");
        assertNotNull(recruitmentDriveController, "RecruitmentDriveController bean must be loaded");
        assertNotNull(eligibilityService, "Bridged EligibilityService bean must be loaded");
    }

    @Test
    @DisplayName("Component scan boundaries strictly exclude legacy Swing UI and TCP networking components")
    void componentScanBoundariesArePreserved() {
        // Assert that legacy desktop UI Frame is not present in Spring context
        assertThrows(NoSuchBeanDefinitionException.class, () ->
                applicationContext.getBean("mainFrame")
        );
        assertThrows(NoSuchBeanDefinitionException.class, () ->
                applicationContext.getBean("hireFlowTcpServer")
        );
    }
}
