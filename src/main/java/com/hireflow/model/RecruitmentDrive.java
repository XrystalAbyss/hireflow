package com.hireflow.model;

import com.hireflow.exception.ValidationException;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a company's recruitment drive on campus.
 */
public class RecruitmentDrive {
    private final String id;
    private String companyId;
    private String jobTitle;
    private double minimumCgpa;
    private final Set<String> eligibleBranches = new HashSet<>();
    private LocalDate applicationDeadline;

    public RecruitmentDrive(String id, String companyId, String jobTitle, double minimumCgpa,
                            Set<String> eligibleBranches, LocalDate applicationDeadline) {
        validateId(id);
        validateCompanyId(companyId);
        validateJobTitle(jobTitle);
        validateMinimumCgpa(minimumCgpa);
        validateApplicationDeadline(applicationDeadline);

        this.id = id.trim().toUpperCase();
        this.companyId = companyId.trim().toUpperCase();
        this.jobTitle = jobTitle.trim();
        this.minimumCgpa = Math.round(minimumCgpa * 100.0) / 100.0;
        this.applicationDeadline = applicationDeadline;

        if (eligibleBranches != null) {
            for (String branch : eligibleBranches) {
                if (branch != null && !branch.trim().isEmpty()) {
                    this.eligibleBranches.add(branch.trim().toUpperCase());
                }
            }
        }
    }

    public static void validateId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("Recruitment Drive ID cannot be null or blank.");
        }
    }

    public static void validateCompanyId(String companyId) {
        if (companyId == null || companyId.trim().isEmpty()) {
            throw new ValidationException("Company ID cannot be null or blank.");
        }
    }

    public static void validateJobTitle(String jobTitle) {
        if (jobTitle == null || jobTitle.trim().isEmpty()) {
            throw new ValidationException("Job title cannot be null or blank.");
        }
    }

    public static void validateMinimumCgpa(double cgpa) {
        if (cgpa < 0.0 || cgpa > 10.0) {
            throw new ValidationException("Minimum CGPA must be between 0.00 and 10.00. Provided: " + cgpa);
        }
    }

    public static void validateApplicationDeadline(LocalDate deadline) {
        if (deadline == null) {
            throw new ValidationException("Application deadline cannot be null.");
        }
    }

    public boolean isBranchEligible(String branch) {
        if (eligibleBranches.isEmpty() || eligibleBranches.contains("*") || eligibleBranches.contains("ALL")) {
            return true;
        }
        if (branch == null) return false;
        return eligibleBranches.contains(branch.trim().toUpperCase());
    }

    public boolean isCgpaEligible(double cgpa) {
        return cgpa >= this.minimumCgpa;
    }

    public boolean isDeadlinePassed(LocalDate currentDate) {
        if (currentDate == null) return false;
        return currentDate.isAfter(this.applicationDeadline);
    }

    public String getId() {
        return id;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        validateCompanyId(companyId);
        this.companyId = companyId.trim().toUpperCase();
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        validateJobTitle(jobTitle);
        this.jobTitle = jobTitle.trim();
    }

    public double getMinimumCgpa() {
        return minimumCgpa;
    }

    public double getMinCgpa() {
        return minimumCgpa;
    }

    public void setMinimumCgpa(double minimumCgpa) {
        validateMinimumCgpa(minimumCgpa);
        this.minimumCgpa = Math.round(minimumCgpa * 100.0) / 100.0;
    }

    /**
     * Returns an unmodifiable view of eligible branches to prevent external mutation.
     */
    public Set<String> getEligibleBranches() {
        return Collections.unmodifiableSet(eligibleBranches);
    }

    public void setEligibleBranches(Set<String> branches) {
        this.eligibleBranches.clear();
        if (branches != null) {
            for (String b : branches) {
                if (b != null && !b.trim().isEmpty()) {
                    this.eligibleBranches.add(b.trim().toUpperCase());
                }
            }
        }
    }

    public void addEligibleBranch(String branch) {
        if (branch != null && !branch.trim().isEmpty()) {
            this.eligibleBranches.add(branch.trim().toUpperCase());
        }
    }

    public LocalDate getApplicationDeadline() {
        return applicationDeadline;
    }

    public void setApplicationDeadline(LocalDate applicationDeadline) {
        validateApplicationDeadline(applicationDeadline);
        this.applicationDeadline = applicationDeadline;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecruitmentDrive drive)) return false;
        return Objects.equals(id, drive.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RecruitmentDrive{" +
                "id='" + id + '\'' +
                ", companyId='" + companyId + '\'' +
                ", jobTitle='" + jobTitle + '\'' +
                ", minimumCgpa=" + minimumCgpa +
                ", eligibleBranches=" + eligibleBranches +
                ", applicationDeadline=" + applicationDeadline +
                '}';
    }
}
