package com.hireflow.service;

import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.repository.DriveRepository;
import com.hireflow.repository.InMemoryCompanyRepository;
import com.hireflow.repository.InMemoryDriveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompanyService - Corporate Partner Management Tests")
class CompanyServiceTest {

    private CompanyRepository companyRepo;
    private DriveRepository driveRepo;
    private CompanyService companyService;

    @BeforeEach
    void setUp() {
        companyRepo = new InMemoryCompanyRepository();
        driveRepo = new InMemoryDriveRepository();
        companyService = new CompanyService(companyRepo, driveRepo, null);
    }

    @Test
    @DisplayName("registerCompany: successfully adds valid company")
    void testRegisterCompanySuccess() {
        Company comp = companyService.registerCompany("CMP-001", "Acme Corp", "Technology");
        assertNotNull(comp);
        assertEquals("CMP-001", comp.getId());
        assertEquals("Acme Corp", comp.getName());
        assertEquals("Technology", comp.getIndustry());

        assertEquals(1, companyService.getAllCompanies().size());
        assertEquals(1, companyService.getTotalCompaniesCount());
    }

    @Test
    @DisplayName("registerCompany: duplicate ID throws DuplicateEntityException")
    void testRegisterDuplicateIdThrows() {
        companyService.registerCompany("CMP-001", "Acme Corp", "Technology");
        assertThrows(DuplicateEntityException.class, () ->
                companyService.registerCompany("CMP-001", "Beta Corp", "Finance")
        );
    }

    @Test
    @DisplayName("registerCompany: duplicate Name throws DuplicateEntityException")
    void testRegisterDuplicateNameThrows() {
        companyService.registerCompany("CMP-001", "Acme Corp", "Technology");
        assertThrows(DuplicateEntityException.class, () ->
                companyService.registerCompany("CMP-002", "Acme Corp", "Finance")
        );
    }

    @Test
    @DisplayName("registerCompany: invalid inputs throw ValidationException")
    void testRegisterInvalidInputsThrow() {
        assertThrows(ValidationException.class, () -> companyService.registerCompany("", "Acme", "Tech"));
        assertThrows(ValidationException.class, () -> companyService.registerCompany("CMP-1", "", "Tech"));
        assertThrows(ValidationException.class, () -> companyService.registerCompany("CMP-1", "Acme", ""));
    }

    @Test
    @DisplayName("updateCompany: successfully updates permitted fields")
    void testUpdateCompanySuccess() {
        companyService.registerCompany("CMP-001", "Acme Corp", "Technology");
        Company updated = companyService.updateCompany("CMP-001", "Acme Global", "FinTech");

        assertEquals("Acme Global", updated.getName());
        assertEquals("FinTech", updated.getIndustry());

        Company retrieved = companyService.getCompanyOrThrow("CMP-001");
        assertEquals("Acme Global", retrieved.getName());
        assertEquals("FinTech", retrieved.getIndustry());
    }

    @Test
    @DisplayName("updateCompany: renaming to existing name of another company throws DuplicateEntityException")
    void testUpdateCompanyDuplicateNameThrows() {
        companyService.registerCompany("CMP-001", "Acme Corp", "Technology");
        companyService.registerCompany("CMP-002", "Beta Corp", "Finance");

        assertThrows(DuplicateEntityException.class, () ->
                companyService.updateCompany("CMP-002", "Acme Corp", "Finance")
        );
    }

    @Test
    @DisplayName("updateCompany: non-existent ID throws EntityNotFoundException")
    void testUpdateNonExistentThrows() {
        assertThrows(EntityNotFoundException.class, () ->
                companyService.updateCompany("CMP-999", "Ghost Corp", "Finance")
        );
    }

    @Test
    @DisplayName("deleteCompany: deletes company with no linked drives")
    void testDeleteCompanySuccess() {
        companyService.registerCompany("CMP-001", "Acme Corp", "Technology");
        assertTrue(companyService.deleteCompany("CMP-001"));
        assertEquals(0, companyService.getAllCompanies().size());
    }

    @Test
    @DisplayName("deleteCompany: throws ValidationException if drives are registered")
    void testDeleteCompanyWithLinkedDrivesThrows() {
        companyService.registerCompany("CMP-001", "Acme Corp", "Technology");
        driveRepo.add(new RecruitmentDrive("DRV-001", "CMP-001", "Dev", 8.0, Set.of("CSE"), LocalDate.now().plusDays(5)));

        assertThrows(ValidationException.class, () ->
                companyService.deleteCompany("CMP-001")
        );
    }
}
