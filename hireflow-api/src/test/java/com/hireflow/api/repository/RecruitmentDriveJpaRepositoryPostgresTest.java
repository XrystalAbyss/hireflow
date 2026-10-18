package com.hireflow.api.repository;

import com.hireflow.api.entity.CompanyJpaEntity;
import com.hireflow.api.entity.RecruitmentDriveJpaEntity;
import com.hireflow.model.RecruitmentDrive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RecruitmentDriveJpaRepositoryPostgresTest {

    @Autowired
    private RecruitmentDriveJpaRepository recruitmentDriveJpaRepository;

    @Autowired
    private CompanyJpaRepository companyJpaRepository;

    @Test
    @DisplayName("Read recruitment drives and navigate company relationship via JPA")
    void readDrivesAndNavigateCompany() {
        List<RecruitmentDriveJpaEntity> drives = recruitmentDriveJpaRepository.findAll();
        assertNotNull(drives, "Drives list must not be null");

        for (RecruitmentDriveJpaEntity drive : drives) {
            assertNotNull(drive.getId(), "Drive ID must not be null");
            assertNotNull(drive.getJobTitle(), "Drive job title must not be null");
            assertNotNull(drive.getMinimumCgpa(), "Drive minimum CGPA must not be null");
            assertNotNull(drive.getApplicationDeadline(), "Application deadline must not be null");
            assertNotNull(drive.getCompany(), "Associated company must not be null");
            assertNotNull(drive.getCompany().getId(), "Associated company ID must not be null");
            assertNotNull(drive.getEligibleBranches(), "Eligible branches collection must not be null");

            // Convert to domain model and assert domain invariants hold
            RecruitmentDrive domain = drive.toDomain();
            assertEquals(drive.getId(), domain.getId());
            assertEquals(drive.getCompany().getId(), domain.getCompanyId());
            assertEquals(drive.getJobTitle(), domain.getJobTitle());
            assertEquals(drive.getMinimumCgpa().doubleValue(), domain.getMinimumCgpa());
            assertEquals(drive.isClosed(), domain.isClosed());
            assertEquals(drive.getApplicationDeadline(), domain.getApplicationDeadline());
        }
    }

    @Test
    @DisplayName("Verify @ElementCollection eligible branches relationship mapping")
    @Transactional
    void elementCollectionEligibleBranchesMapping() {
        String testCompanyId = "TEST-JPA-CORP-DRV";
        String testDriveId = "TEST-JPA-DRV-001";

        CompanyJpaEntity company = new CompanyJpaEntity(
                testCompanyId, "Test JPA Drive Corp", "Software Engineering",
                LocalDateTime.now(), LocalDateTime.now()
        );
        companyJpaRepository.save(company);

        Set<String> branches = Set.of("CSE", "ECE", "IT");
        RecruitmentDriveJpaEntity drive = new RecruitmentDriveJpaEntity(
                testDriveId,
                company,
                "Senior Cloud Architect",
                BigDecimal.valueOf(8.25),
                branches,
                LocalDate.now().plusMonths(1),
                false,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        recruitmentDriveJpaRepository.save(drive);

        // Retrieve by ID and verify branches collection
        Optional<RecruitmentDriveJpaEntity> loadedOpt = recruitmentDriveJpaRepository.findById(testDriveId);
        assertTrue(loadedOpt.isPresent(), "Drive must be present");
        RecruitmentDriveJpaEntity loaded = loadedOpt.get();

        assertEquals(3, loaded.getEligibleBranches().size());
        assertTrue(loaded.getEligibleBranches().contains("CSE"));
        assertTrue(loaded.getEligibleBranches().contains("ECE"));
        assertTrue(loaded.getEligibleBranches().contains("IT"));
        assertEquals("Senior Cloud Architect", loaded.getJobTitle());
        assertEquals("Test JPA Drive Corp", loaded.getCompany().getName());

        // Domain conversion verification
        RecruitmentDrive domain = loaded.toDomain();
        assertTrue(domain.isBranchEligible("CSE"));
        assertTrue(domain.isBranchEligible("ECE"));
        assertFalse(domain.isBranchEligible("MECH"));
        assertTrue(domain.isCgpaEligible(8.5));
        assertFalse(domain.isCgpaEligible(7.5));
        assertFalse(domain.isDeadlinePassed(LocalDate.now()));

        // Clean up
        recruitmentDriveJpaRepository.deleteById(testDriveId);
        companyJpaRepository.deleteById(testCompanyId);
    }

    @Test
    @DisplayName("Filter active recruitment drives using JPA query method")
    void filterActiveDrives() {
        LocalDate today = LocalDate.now();
        Page<RecruitmentDriveJpaEntity> activePage = recruitmentDriveJpaRepository
                .findByIsClosedFalseAndApplicationDeadlineGreaterThanEqual(
                        today, PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "applicationDeadline"))
                );

        assertNotNull(activePage);
        for (RecruitmentDriveJpaEntity drive : activePage.getContent()) {
            assertFalse(drive.isClosed(), "Active drive must not be marked closed");
            assertFalse(drive.getApplicationDeadline().isBefore(today), "Active drive deadline must not be before today");
        }
    }
}
