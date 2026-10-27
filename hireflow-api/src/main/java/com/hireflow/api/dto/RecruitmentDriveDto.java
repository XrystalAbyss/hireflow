package com.hireflow.api.dto;

import com.hireflow.api.entity.RecruitmentDriveJpaEntity;
import com.hireflow.model.RecruitmentDrive;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;

/**
 * Immutable Data Transfer Object representing public recruitment drive catalog information.
 * <p>
 * Does NOT expose applicant lists, candidate identifiers, session timetables, or internal audit fields.
 */
public record RecruitmentDriveDto(
        String id,
        String companyId,
        String companyName,
        String jobTitle,
        double minimumCgpa,
        Set<String> eligibleBranches,
        LocalDate applicationDeadline,
        boolean isClosed,
        LocalDateTime createdAt
) {
    public static RecruitmentDriveDto fromEntity(RecruitmentDriveJpaEntity entity) {
        if (entity == null) return null;
        return new RecruitmentDriveDto(
                entity.getId(),
                entity.getCompany() != null ? entity.getCompany().getId() : null,
                entity.getCompany() != null ? entity.getCompany().getName() : null,
                entity.getJobTitle(),
                entity.getMinimumCgpa() != null ? entity.getMinimumCgpa().doubleValue() : 0.0,
                Collections.unmodifiableSet(entity.getEligibleBranches()),
                entity.getApplicationDeadline(),
                entity.isClosed(),
                entity.getCreatedAt()
        );
    }

    public static RecruitmentDriveDto fromDomain(RecruitmentDrive domain, String companyName) {
        if (domain == null) return null;
        return new RecruitmentDriveDto(
                domain.getId(),
                domain.getCompanyId(),
                companyName,
                domain.getJobTitle(),
                domain.getMinimumCgpa(),
                Collections.unmodifiableSet(domain.getEligibleBranches()),
                domain.getApplicationDeadline(),
                domain.isClosed(),
                null
        );
    }
}
