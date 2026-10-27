package com.hireflow.api.entity;

import com.hireflow.model.RecruitmentDrive;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Read-only JPA entity mapping for the PostgreSQL {@code recruitment_drives} table
 * and {@code drive_eligible_branches} collection table.
 * <p>
 * Strictly aligned with Flyway V1 &amp; V3 migration schema and validated via Hibernate {@code ddl-auto=validate}.
 */
@Entity
@Table(name = "recruitment_drives")
public class RecruitmentDriveJpaEntity {

    @Id
    @Column(name = "id", length = 32, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyJpaEntity company;

    @Column(name = "job_title", length = 150, nullable = false)
    private String jobTitle;

    @Column(name = "minimum_cgpa", precision = 4, scale = 2, nullable = false)
    private BigDecimal minimumCgpa;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "drive_eligible_branches",
            joinColumns = @JoinColumn(name = "drive_id")
    )
    @Column(name = "branch", length = 20, nullable = false)
    private Set<String> eligibleBranches = new HashSet<>();

    @Column(name = "application_deadline", nullable = false)
    private LocalDate applicationDeadline;

    @Column(name = "is_closed", nullable = false)
    private boolean isClosed;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public RecruitmentDriveJpaEntity() {
        // Required by JPA specification
    }

    public RecruitmentDriveJpaEntity(String id, CompanyJpaEntity company, String jobTitle,
                                     BigDecimal minimumCgpa, Set<String> eligibleBranches,
                                     LocalDate applicationDeadline, boolean isClosed,
                                     LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.company = company;
        this.jobTitle = jobTitle;
        this.minimumCgpa = minimumCgpa;
        if (eligibleBranches != null) {
            this.eligibleBranches.addAll(eligibleBranches);
        }
        this.applicationDeadline = applicationDeadline;
        this.isClosed = isClosed;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public CompanyJpaEntity getCompany() {
        return company;
    }

    public void setCompany(CompanyJpaEntity company) {
        this.company = company;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public BigDecimal getMinimumCgpa() {
        return minimumCgpa;
    }

    public void setMinimumCgpa(BigDecimal minimumCgpa) {
        this.minimumCgpa = minimumCgpa;
    }

    public Set<String> getEligibleBranches() {
        return eligibleBranches;
    }

    public void setEligibleBranches(Set<String> eligibleBranches) {
        this.eligibleBranches = eligibleBranches != null ? eligibleBranches : new HashSet<>();
    }

    public LocalDate getApplicationDeadline() {
        return applicationDeadline;
    }

    public void setApplicationDeadline(LocalDate applicationDeadline) {
        this.applicationDeadline = applicationDeadline;
    }

    public boolean isClosed() {
        return isClosed;
    }

    public void setClosed(boolean closed) {
        isClosed = closed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Converts this JPA persistence entity into an immutable, domain-validated {@link RecruitmentDrive} instance.
     */
    public RecruitmentDrive toDomain() {
        return new RecruitmentDrive(
                this.id,
                this.company != null ? this.company.getId() : null,
                this.jobTitle,
                this.minimumCgpa != null ? this.minimumCgpa.doubleValue() : 0.0,
                this.eligibleBranches,
                this.applicationDeadline,
                this.isClosed
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecruitmentDriveJpaEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RecruitmentDriveJpaEntity{" +
                "id='" + id + '\'' +
                ", jobTitle='" + jobTitle + '\'' +
                ", companyId='" + (company != null ? company.getId() : "null") + '\'' +
                ", applicationDeadline=" + applicationDeadline +
                ", isClosed=" + isClosed +
                '}';
    }
}
