package com.hireflow.model;

import com.hireflow.exception.ValidationException;

import java.util.Objects;

/**
 * Represents a visiting company participating in campus recruitment.
 */
public class Company {
    private final String id;
    private String name;
    private String industry;

    public Company(String id, String name, String industry) {
        validateId(id);
        validateName(name);
        validateIndustry(industry);

        this.id = id.trim().toUpperCase();
        this.name = name.trim();
        this.industry = industry.trim();
    }

    public static void validateId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("Company ID cannot be null or blank.");
        }
    }

    public static void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Company name cannot be null or blank.");
        }
    }

    public static void validateIndustry(String industry) {
        if (industry == null || industry.trim().isEmpty()) {
            throw new ValidationException("Industry cannot be null or blank.");
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        validateName(name);
        this.name = name.trim();
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        validateIndustry(industry);
        this.industry = industry.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Company company)) return false;
        return Objects.equals(id, company.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Company{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", industry='" + industry + '\'' +
                '}';
    }
}
