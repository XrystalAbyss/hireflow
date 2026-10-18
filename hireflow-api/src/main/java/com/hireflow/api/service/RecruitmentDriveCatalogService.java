package com.hireflow.api.service;

import com.hireflow.api.dto.PagedResponse;
import com.hireflow.api.dto.RecruitmentDriveDto;
import com.hireflow.api.entity.RecruitmentDriveJpaEntity;
import com.hireflow.api.repository.RecruitmentDriveJpaRepository;
import com.hireflow.model.RecruitmentDrive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only catalog service for active campus recruitment drives.
 * <p>
 * Uses Spring Data JPA repositories with explicit {@code @Transactional(readOnly = true)} boundaries.
 */
@Service
@Transactional(readOnly = true)
public class RecruitmentDriveCatalogService {

    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of(
            "id", "jobTitle", "minimumCgpa", "applicationDeadline", "createdAt"
    );

    private final RecruitmentDriveJpaRepository recruitmentDriveJpaRepository;

    public RecruitmentDriveCatalogService(RecruitmentDriveJpaRepository recruitmentDriveJpaRepository) {
        this.recruitmentDriveJpaRepository = Objects.requireNonNull(
                recruitmentDriveJpaRepository, "RecruitmentDriveJpaRepository cannot be null"
        );
    }

    public PagedResponse<RecruitmentDriveDto> getDrives(String companyId, boolean activeOnly,
                                                       int page, int size, String sortBy, String direction) {
        int validatedPage = Math.max(0, page);
        int validatedSize = Math.clamp(size, 1, 100);

        String property = ALLOWED_SORT_PROPERTIES.contains(sortBy) ? sortBy : "applicationDeadline";
        Sort.Direction sortDirection = "DESC".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(validatedPage, validatedSize, Sort.by(sortDirection, property));

        LocalDate today = LocalDate.now();
        Page<RecruitmentDriveJpaEntity> entityPage;

        boolean hasCompany = companyId != null && !companyId.trim().isEmpty();
        String normalizedCompanyId = hasCompany ? companyId.trim().toUpperCase() : null;

        if (hasCompany && activeOnly) {
            entityPage = recruitmentDriveJpaRepository.findByCompanyIdAndIsClosedFalseAndApplicationDeadlineGreaterThanEqual(
                    normalizedCompanyId, today, pageable);
        } else if (hasCompany) {
            entityPage = recruitmentDriveJpaRepository.findByCompanyId(normalizedCompanyId, pageable);
        } else if (activeOnly) {
            entityPage = recruitmentDriveJpaRepository.findByIsClosedFalseAndApplicationDeadlineGreaterThanEqual(today, pageable);
        } else {
            entityPage = recruitmentDriveJpaRepository.findAll(pageable);
        }

        Page<RecruitmentDriveDto> dtoPage = entityPage.map(RecruitmentDriveDto::fromEntity);
        return PagedResponse.fromPage(dtoPage);
    }

    public Optional<RecruitmentDriveDto> getDriveById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        return recruitmentDriveJpaRepository.findById(id.trim().toUpperCase())
                .map(RecruitmentDriveDto::fromEntity);
    }

    public Optional<RecruitmentDrive> getDomainDriveById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        return recruitmentDriveJpaRepository.findById(id.trim().toUpperCase())
                .map(RecruitmentDriveJpaEntity::toDomain);
    }

    public long countDrives() {
        return recruitmentDriveJpaRepository.count();
    }
}
