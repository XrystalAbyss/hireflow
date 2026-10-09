package com.hireflow.model;

import com.hireflow.exception.ValidationException;

import java.util.Objects;

/**
 * Represents a company interviewer conducting candidate evaluations.
 */
public class Interviewer {
    private final String id;
    private String name;
    private String companyId;

    public Interviewer(String id, String name, String companyId) {
        validateId(id);
        validateName(name);
        validateCompanyId(companyId);

        this.id = id.trim().toUpperCase();
        this.name = name.trim();
        this.companyId = companyId.trim().toUpperCase();
    }

    public static void validateId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("Interviewer ID cannot be null or blank.");
        }
    }

    public static void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Interviewer name cannot be null or blank.");
        }
    }

    public static void validateCompanyId(String companyId) {
        if (companyId == null || companyId.trim().isEmpty()) {
            throw new ValidationException("Company ID cannot be null or blank.");
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

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        validateCompanyId(companyId);
        this.companyId = companyId.trim().toUpperCase();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Interviewer that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Interviewer{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", companyId='" + companyId + '\'' +
                '}';
    }
}
