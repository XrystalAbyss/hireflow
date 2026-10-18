package com.hireflow.api.dto.officer;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hireflow.model.RecruitmentDrive;

import java.time.LocalDate;
import java.util.Set;

public record OfficerDriveDetailDto(
        String id,
        String companyId,
        String companyName,
        String jobTitle,
        double minimumCgpa,
        Set<String> eligibleBranches,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate applicationDeadline,
        boolean closed,
        int totalApplications,
        int shortlistedApplications
) {
    public static OfficerDriveDetailDto fromDomain(
            RecruitmentDrive drive,
            String companyName,
            int totalApplications,
            int shortlistedApplications) {
        return new OfficerDriveDetailDto(
                drive.getId(),
                drive.getCompanyId(),
                companyName != null ? companyName : drive.getCompanyId(),
                drive.getJobTitle(),
                drive.getMinCgpa(),
                drive.getEligibleBranches(),
                drive.getApplicationDeadline(),
                drive.isClosed(),
                totalApplications,
                shortlistedApplications
        );
    }
}
