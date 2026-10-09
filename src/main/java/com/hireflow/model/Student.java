package com.hireflow.model;

import com.hireflow.exception.ValidationException;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Represents an enrolled university student.
 */
public class Student {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final String id;
    private String name;
    private String email;
    private String branch;
    private double cgpa;
    private int graduationYear;
    private boolean active;

    public Student(String id, String name, String email, String branch, double cgpa, int graduationYear) {
        this(id, name, email, branch, cgpa, graduationYear, true);
    }

    public Student(String id, String name, String email, String branch, double cgpa, int graduationYear, boolean active) {
        validateId(id);
        validateName(name);
        validateEmail(email);
        validateBranch(branch);
        validateCgpa(cgpa);
        validateGraduationYear(graduationYear);

        this.id = id.trim().toUpperCase();
        this.name = name.trim();
        this.email = email.trim().toLowerCase();
        this.branch = branch.trim().toUpperCase();
        this.cgpa = Math.round(cgpa * 100.0) / 100.0;
        this.graduationYear = graduationYear;
        this.active = active;
    }

    public static void validateId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("Student ID cannot be null or blank.");
        }
    }

    public static void validateName(String name) {
        if (name == null || name.trim().length() < 2) {
            throw new ValidationException("Student name must be at least 2 characters.");
        }
    }

    public static void validateEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new ValidationException("Invalid student email format: " + email);
        }
    }

    public static void validateBranch(String branch) {
        if (branch == null || branch.trim().isEmpty()) {
            throw new ValidationException("Branch cannot be null or blank.");
        }
    }

    public static void validateCgpa(double cgpa) {
        if (cgpa < 0.0 || cgpa > 10.0) {
            throw new ValidationException("CGPA must be between 0.00 and 10.00. Provided: " + cgpa);
        }
    }

    public static void validateGraduationYear(int year) {
        if (year < 2000 || year > 2100) {
            throw new ValidationException("Graduation year must be between 2000 and 2100. Provided: " + year);
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        validateEmail(email);
        this.email = email.trim().toLowerCase();
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        validateBranch(branch);
        this.branch = branch.trim().toUpperCase();
    }

    public double getCgpa() {
        return cgpa;
    }

    public void setCgpa(double cgpa) {
        validateCgpa(cgpa);
        this.cgpa = Math.round(cgpa * 100.0) / 100.0;
    }

    public int getGraduationYear() {
        return graduationYear;
    }

    public void setGraduationYear(int graduationYear) {
        validateGraduationYear(graduationYear);
        this.graduationYear = graduationYear;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Student student)) return false;
        return Objects.equals(id, student.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Student{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", branch='" + branch + '\'' +
                ", cgpa=" + cgpa +
                ", graduationYear=" + graduationYear +
                ", active=" + active +
                '}';
    }
}
