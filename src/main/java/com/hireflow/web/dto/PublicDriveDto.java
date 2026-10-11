package com.hireflow.web.dto;

import com.hireflow.model.RecruitmentDrive;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Data Transfer Object representing public recruitment drive details presented
 * on the HireFlow web portal. Contains zero candidate PII or sensitive internal metadata.
 */
public final class PublicDriveDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String driveId;
    private final String companyId;
    private final String companyName;
    private final String jobTitle;
    private final double minCgpa;
    private final List<String> eligibleBranches;
    private final LocalDate applicationDeadline;
    private final boolean active;
    private final long daysRemaining;
    private final boolean expired;
    private final boolean closed;

    public PublicDriveDto(String driveId,
                          String companyId,
                          String companyName,
                          String jobTitle,
                          double minCgpa,
                          Collection<String> eligibleBranches,
                          LocalDate applicationDeadline,
                          boolean active,
                          long daysRemaining) {
        this(driveId, companyId, companyName, jobTitle, minCgpa, eligibleBranches, applicationDeadline, active, daysRemaining, !active, false);
    }

    public PublicDriveDto(String driveId,
                          String companyId,
                          String companyName,
                          String jobTitle,
                          double minCgpa,
                          Collection<String> eligibleBranches,
                          LocalDate applicationDeadline,
                          boolean active,
                          long daysRemaining,
                          boolean expired,
                          boolean closed) {
        this.driveId = Objects.requireNonNull(driveId, "driveId cannot be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId cannot be null");
        this.companyName = companyName != null && !companyName.trim().isEmpty() ? companyName.trim() : companyId;
        this.jobTitle = Objects.requireNonNull(jobTitle, "jobTitle cannot be null");
        this.minCgpa = minCgpa;
        this.eligibleBranches = eligibleBranches != null
                ? List.copyOf(new TreeSet<>(eligibleBranches))
                : List.of();
        this.applicationDeadline = Objects.requireNonNull(applicationDeadline, "applicationDeadline cannot be null");
        this.active = active;
        this.daysRemaining = daysRemaining;
        this.expired = expired;
        this.closed = closed;
    }

    /**
     * Factory constructor extracting public fields from a domain entity.
     */
    public static PublicDriveDto from(RecruitmentDrive drive, String companyName) {
        Objects.requireNonNull(drive, "drive entity cannot be null");
        LocalDate today = LocalDate.now();
        boolean isClosed = drive.isClosed();
        boolean isExpired = drive.isDeadlinePassed(today);
        boolean isActive = !isClosed && !isExpired;
        long days = ChronoUnit.DAYS.between(today, drive.getApplicationDeadline());
        if (days < 0) {
            days = 0;
        }

        return new PublicDriveDto(
                drive.getId(),
                drive.getCompanyId(),
                companyName,
                drive.getJobTitle(),
                drive.getMinimumCgpa(),
                drive.getEligibleBranches(),
                drive.getApplicationDeadline(),
                isActive,
                days,
                isExpired,
                isClosed
        );
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

    public double getMinCgpa() {
        return minCgpa;
    }

    public List<String> getEligibleBranches() {
        return eligibleBranches;
    }

    public LocalDate getApplicationDeadline() {
        return applicationDeadline;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isExpired() {
        return expired;
    }

    public long getDaysRemaining() {
        return daysRemaining;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PublicDriveDto that = (PublicDriveDto) o;
        return Objects.equals(driveId, that.driveId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(driveId);
    }

    @Override
    public String toString() {
        return "PublicDriveDto{" +
                "driveId='" + driveId + '\'' +
                ", companyName='" + companyName + '\'' +
                ", jobTitle='" + jobTitle + '\'' +
                ", active=" + active +
                '}';
    }
}
