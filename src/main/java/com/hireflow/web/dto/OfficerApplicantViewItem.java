package com.hireflow.web.dto;

import com.hireflow.model.Application;
import com.hireflow.model.ApplicationStatus;
import com.hireflow.model.Company;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Student;
import com.hireflow.service.EligibilityService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * View model presenting applicant details to placement officers,
 * including candidate academic metrics, current application status, and server-evaluated eligibility.
 */
public class OfficerApplicantViewItem {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

    private final String applicationId;
    private final String studentId;
    private final String studentName;
    private final String studentBranch;
    private final double studentCgpa;
    private final int graduationYear;
    private final boolean studentActive;
    private final String driveId;
    private final String companyId;
    private final String companyName;
    private final String jobTitle;
    private final LocalDateTime appliedAt;
    private final ApplicationStatus status;
    private final String remarks;
    private final boolean eligible;
    private final String eligibilityReasons;
    private final boolean canShortlist;

    public OfficerApplicantViewItem(Application app, Student student, RecruitmentDrive drive,
                                    Company company, EligibilityService.EligibilityResult eligibility) {
        this.applicationId = app != null ? app.getId() : "";
        this.studentId = student != null ? student.getId() : (app != null ? app.getStudentId() : "");
        this.studentName = student != null ? student.getName() : "Unknown Student";
        this.studentBranch = student != null ? student.getBranch() : "-";
        this.studentCgpa = student != null ? student.getCgpa() : 0.0;
        this.graduationYear = student != null ? student.getGraduationYear() : 0;
        this.studentActive = student == null || student.isActive();
        this.driveId = drive != null ? drive.getId() : (app != null ? app.getDriveId() : "");
        this.companyId = company != null ? company.getId() : (drive != null ? drive.getCompanyId() : "");
        this.companyName = company != null ? company.getName() : (drive != null ? drive.getCompanyId() : "-");
        this.jobTitle = drive != null ? drive.getJobTitle() : "-";
        this.appliedAt = app != null ? app.getAppliedAt() : LocalDateTime.now();
        this.status = app != null ? app.getStatus() : ApplicationStatus.APPLIED;
        this.remarks = app != null ? app.getRemarks() : "";
        this.eligible = eligibility != null && eligibility.isEligible();
        this.eligibilityReasons = eligibility != null ? eligibility.getFormattedReason() : "";

        // Can shortlist if in APPLIED or ELIGIBLE state and satisfies academic eligibility
        this.canShortlist = (this.status == ApplicationStatus.APPLIED || this.status == ApplicationStatus.ELIGIBLE)
                && this.eligible;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public String getStudentBranch() {
        return studentBranch;
    }

    public double getStudentCgpa() {
        return studentCgpa;
    }

    public String getFormattedCgpa() {
        return String.format("%.2f", studentCgpa);
    }

    public int getGraduationYear() {
        return graduationYear;
    }

    public boolean isStudentActive() {
        return studentActive;
    }

    public String getDriveId() {
        return driveId;
    }

    public String getCompanyId() {
        return companyId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public String getFormattedAppliedAt() {
        return appliedAt != null ? appliedAt.format(FORMATTER) : "-";
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public String getRemarks() {
        return remarks;
    }

    public boolean isEligible() {
        return eligible;
    }

    public String getEligibilityReasons() {
        return eligibilityReasons;
    }

    public boolean isCanShortlist() {
        return canShortlist;
    }

    public boolean isShortlisted() {
        return status == ApplicationStatus.SHORTLISTED;
    }
}
