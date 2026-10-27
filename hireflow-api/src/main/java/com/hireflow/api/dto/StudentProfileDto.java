package com.hireflow.api.dto;

import com.hireflow.model.Student;

/**
 * Immutable Data Transfer Object representing the authenticated student's academic profile.
 * Does NOT expose internal database credentials, password hashes, or confidential notes.
 */
public record StudentProfileDto(
        String id,
        String name,
        String email,
        String branch,
        double cgpa,
        int graduationYear,
        boolean active
) {
    public static StudentProfileDto fromDomain(Student student) {
        if (student == null) return null;
        return new StudentProfileDto(
                student.getId(),
                student.getName(),
                student.getEmail(),
                student.getBranch(),
                student.getCgpa(),
                student.getGraduationYear(),
                student.isActive()
        );
    }
}
