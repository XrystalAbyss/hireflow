package com.hireflow.api.dto;

import com.hireflow.api.entity.CompanyJpaEntity;
import com.hireflow.model.Company;

import java.time.LocalDateTime;

/**
 * Immutable Data Transfer Object representing public company catalog information.
 */
public record CompanyDto(
        String id,
        String name,
        String industry,
        LocalDateTime createdAt
) {
    public static CompanyDto fromEntity(CompanyJpaEntity entity) {
        if (entity == null) return null;
        return new CompanyDto(
                entity.getId(),
                entity.getName(),
                entity.getIndustry(),
                entity.getCreatedAt()
        );
    }

    public static CompanyDto fromDomain(Company domain) {
        if (domain == null) return null;
        return new CompanyDto(
                domain.getId(),
                domain.getName(),
                domain.getIndustry(),
                null
        );
    }
}
