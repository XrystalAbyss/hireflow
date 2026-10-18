package com.hireflow.api.service;

import com.hireflow.api.dto.CompanyDto;
import com.hireflow.api.dto.PagedResponse;
import com.hireflow.api.entity.CompanyJpaEntity;
import com.hireflow.api.repository.CompanyJpaRepository;
import com.hireflow.model.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only catalog service for visiting corporate partners.
 * <p>
 * Uses Spring Data JPA repositories with explicit {@code @Transactional(readOnly = true)} boundaries.
 */
@Service
@Transactional(readOnly = true)
public class CompanyCatalogService {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("id", "name", "industry", "createdAt");

    private final CompanyJpaRepository companyJpaRepository;

    public CompanyCatalogService(CompanyJpaRepository companyJpaRepository) {
        this.companyJpaRepository = Objects.requireNonNull(companyJpaRepository, "CompanyJpaRepository cannot be null");
    }

    public PagedResponse<CompanyDto> getCompanies(int page, int size, String sortBy, String direction) {
        int validatedPage = Math.max(0, page);
        int validatedSize = Math.clamp(size, 1, 100);

        String property = ALLOWED_SORT_PROPERTIES.contains(sortBy) ? sortBy : "name";
        Sort.Direction sortDirection = "DESC".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;

        Pageable pageable = PageRequest.of(validatedPage, validatedSize, Sort.by(sortDirection, property));
        Page<CompanyJpaEntity> entityPage = companyJpaRepository.findAll(pageable);

        Page<CompanyDto> dtoPage = entityPage.map(CompanyDto::fromEntity);
        return PagedResponse.fromPage(dtoPage);
    }

    public Optional<CompanyDto> getCompanyById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        return companyJpaRepository.findById(id.trim().toUpperCase())
                .map(CompanyDto::fromEntity);
    }

    public Optional<Company> getDomainCompanyById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        return companyJpaRepository.findById(id.trim().toUpperCase())
                .map(CompanyJpaEntity::toDomain);
    }

    public long countCompanies() {
        return companyJpaRepository.count();
    }
}
