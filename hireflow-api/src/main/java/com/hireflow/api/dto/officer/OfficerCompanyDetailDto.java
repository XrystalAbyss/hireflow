package com.hireflow.api.dto.officer;

import com.hireflow.model.Company;

public record OfficerCompanyDetailDto(
        String id,
        String name,
        String industry,
        int activeDrivesCount,
        int totalDrivesCount
) {
    public static OfficerCompanyDetailDto fromDomain(Company company, int activeDrivesCount, int totalDrivesCount) {
        return new OfficerCompanyDetailDto(
                company.getId(),
                company.getName(),
                company.getIndustry(),
                activeDrivesCount,
                totalDrivesCount
        );
    }
}
