package com.hireflow.api.entity;

import com.hireflow.model.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Read-only JPA entity mapping for the PostgreSQL {@code companies} table.
 * <p>
 * Strictly aligned with Flyway V1 migration schema and validated via Hibernate {@code ddl-auto=validate}.
 */
@Entity
@Table(name = "companies")
public class CompanyJpaEntity {

    @Id
    @Column(name = "id", length = 32, nullable = false)
    private String id;

    @Column(name = "name", length = 150, nullable = false, unique = true)
    private String name;

    @Column(name = "industry", length = 150, nullable = false)
    private String industry;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public CompanyJpaEntity() {
        // Required by JPA specification
    }

    public CompanyJpaEntity(String id, String name, String industry, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.industry = industry;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
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
     * Converts this JPA persistence entity into an immutable, domain-validated {@link Company} instance.
     */
    public Company toDomain() {
        return new Company(this.id, this.name, this.industry);
    }

    public static CompanyJpaEntity fromDomain(Company domain) {
        Objects.requireNonNull(domain, "Domain Company cannot be null");
        LocalDateTime now = LocalDateTime.now();
        return new CompanyJpaEntity(domain.getId(), domain.getName(), domain.getIndustry(), now, now);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompanyJpaEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "CompanyJpaEntity{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", industry='" + industry + '\'' +
                '}';
    }
}
