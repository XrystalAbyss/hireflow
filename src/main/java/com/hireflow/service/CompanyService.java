package com.hireflow.service;

import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.Company;
import com.hireflow.repository.CompanyRepository;
import com.hireflow.repository.DriveRepository;
import com.hireflow.repository.InterviewerRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service managing corporate partner registration, updates, and listings.
 */
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final DriveRepository driveRepository;
    private final InterviewerRepository interviewerRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this(companyRepository, null, null);
    }

    public CompanyService(CompanyRepository companyRepository,
                          DriveRepository driveRepository,
                          InterviewerRepository interviewerRepository) {
        this.companyRepository = Objects.requireNonNull(companyRepository, "CompanyRepository cannot be null.");
        this.driveRepository = driveRepository;
        this.interviewerRepository = interviewerRepository;
    }

    public List<Company> getAllCompanies() {
        return companyRepository.findAll();
    }

    public Optional<Company> getCompanyById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        return companyRepository.findById(id.trim().toUpperCase());
    }

    public Company getCompanyOrThrow(String id) {
        return getCompanyById(id)
                .orElseThrow(() -> new EntityNotFoundException("Company with ID '" + id + "' was not found."));
    }

    public Company registerCompany(String id, String name, String industry) {
        Company.validateId(id);
        Company.validateName(name);
        Company.validateIndustry(industry);

        String normId = id.trim().toUpperCase();
        String normName = name.trim();
        String normIndustry = industry.trim();

        if (companyRepository.existsById(normId)) {
            throw new DuplicateEntityException("Company with ID '" + normId + "' already exists.");
        }

        Optional<Company> existingByName = companyRepository.findByName(normName);
        if (existingByName.isPresent()) {
            throw new DuplicateEntityException("Company with name '" + normName + "' already exists.");
        }

        Company company = new Company(normId, normName, normIndustry);
        return companyRepository.add(company);
    }

    public Company registerCompany(Company company) {
        if (company == null) {
            throw new ValidationException("Company cannot be null.");
        }
        return registerCompany(company.getId(), company.getName(), company.getIndustry());
    }

    public Company updateCompany(String id, String name, String industry) {
        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("Company ID is required.");
        }
        Company.validateName(name);
        Company.validateIndustry(industry);

        String normId = id.trim().toUpperCase();
        String normName = name.trim();
        String normIndustry = industry.trim();

        Company existing = getCompanyOrThrow(normId);

        Optional<Company> byName = companyRepository.findByName(normName);
        if (byName.isPresent() && !byName.get().getId().equalsIgnoreCase(normId)) {
            throw new DuplicateEntityException("Company with name '" + normName + "' already exists under another ID.");
        }

        existing.setName(normName);
        existing.setIndustry(normIndustry);
        return companyRepository.update(existing);
    }

    public Company updateCompany(Company company) {
        if (company == null) {
            throw new ValidationException("Company cannot be null.");
        }
        return updateCompany(company.getId(), company.getName(), company.getIndustry());
    }

    public boolean deleteCompany(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        String normId = id.trim().toUpperCase();

        if (driveRepository != null) {
            var drives = driveRepository.findByCompanyId(normId);
            if (!drives.isEmpty()) {
                throw new ValidationException(String.format(
                        "Cannot delete company '%s' because %d recruitment drive(s) are registered under it.",
                        normId, drives.size()
                ));
            }
        }

        if (interviewerRepository != null) {
            var interviewers = interviewerRepository.findByCompanyId(normId);
            if (!interviewers.isEmpty()) {
                throw new ValidationException(String.format(
                        "Cannot delete company '%s' because %d interviewer(s) are registered under it.",
                        normId, interviewers.size()
                ));
            }
        }

        return companyRepository.deleteById(normId);
    }

    public int getTotalCompaniesCount() {
        return companyRepository.count();
    }
}
