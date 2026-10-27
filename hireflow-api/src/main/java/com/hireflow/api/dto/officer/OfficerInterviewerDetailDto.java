package com.hireflow.api.dto.officer;

import com.hireflow.model.Interviewer;

public record OfficerInterviewerDetailDto(
        String id,
        String name,
        String companyId,
        String companyName
) {
    public static OfficerInterviewerDetailDto fromDomain(Interviewer interviewer, String companyName) {
        return new OfficerInterviewerDetailDto(
                interviewer.getId(),
                interviewer.getName(),
                interviewer.getCompanyId(),
                companyName != null ? companyName : interviewer.getCompanyId()
        );
    }
}
