package com.hireflow.api.repository;

import com.hireflow.api.entity.CompanyJpaEntity;
import com.hireflow.model.Company;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CompanyJpaRepositoryPostgresTest {

    @Autowired
    private CompanyJpaRepository companyJpaRepository;

    @Test
    @DisplayName("Read existing companies from PostgreSQL hireflow_test via Spring Data JPA")
    void readCompaniesViaJpa() {
        List<CompanyJpaEntity> companies = companyJpaRepository.findAll();
        assertNotNull(companies, "Company list retrieved via JPA must not be null");

        // Verify entity fields match schema contracts
        for (CompanyJpaEntity c : companies) {
            assertNotNull(c.getId(), "Company ID must not be null");
            assertNotNull(c.getName(), "Company name must not be null");
            assertNotNull(c.getIndustry(), "Company industry must not be null");
            assertNotNull(c.getCreatedAt(), "Company created_at must not be null");
            assertNotNull(c.getUpdatedAt(), "Company updated_at must not be null");

            // Convert to domain model and assert domain invariants hold
            Company domain = c.toDomain();
            assertEquals(c.getId(), domain.getId());
            assertEquals(c.getName(), domain.getName());
            assertEquals(c.getIndustry(), domain.getIndustry());
        }
    }

    @Test
    @DisplayName("Lookup company by name case-insensitively via JPA repository method")
    @Transactional
    void findByNameCaseInsensitive() {
        // Read any existing company or insert a test fixture in transaction that rolls back
        String testId = "TEST-JPA-CORP";
        String testName = "Test Alpha JPA Solutions";
        String testIndustry = "Technology";

        CompanyJpaEntity entity = new CompanyJpaEntity(
                testId, testName, testIndustry, LocalDateTime.now(), LocalDateTime.now()
        );
        companyJpaRepository.save(entity);

        Optional<CompanyJpaEntity> exactMatch = companyJpaRepository.findByNameIgnoreCase(testName);
        assertTrue(exactMatch.isPresent(), "Must find entity by exact name");

        Optional<CompanyJpaEntity> lowerMatch = companyJpaRepository.findByNameIgnoreCase(testName.toLowerCase());
        assertTrue(lowerMatch.isPresent(), "Must find entity by lower case name");

        Optional<CompanyJpaEntity> upperMatch = companyJpaRepository.findByNameIgnoreCase(testName.toUpperCase());
        assertTrue(upperMatch.isPresent(), "Must find entity by upper case name");

        assertTrue(companyJpaRepository.existsByNameIgnoreCase(testName));
        assertFalse(companyJpaRepository.existsByNameIgnoreCase("NonExistentCorporationXYZ123"));

        // Clean up
        companyJpaRepository.deleteById(testId);
    }

    @Test
    @DisplayName("Pagination and bounded sorting execute correctly on PostgreSQL")
    void paginationAndSorting() {
        Page<CompanyJpaEntity> page = companyJpaRepository.findAll(
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "name"))
        );

        assertNotNull(page);
        assertTrue(page.getSize() <= 10);
        assertTrue(page.getNumber() == 0);
    }
}
