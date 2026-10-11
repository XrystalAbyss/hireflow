package com.hireflow.service;

import com.hireflow.exception.ValidationException;
import com.hireflow.model.RecruitmentDrive;
import com.hireflow.model.Student;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service evaluating academic cutoff, permitted branches, and application deadlines.
 */
public class EligibilityService {

    /**
     * Immutable result holding eligibility determination and human-readable reasons.
     */
    public record EligibilityResult(
            boolean isEligible,
            List<String> reasons,
            double studentCgpa,
            double requiredCgpa,
            String studentBranch,
            LocalDate deadline
    ) {
        public EligibilityResult {
            reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
        }

        public String getFormattedReason() {
            return String.join("; ", reasons);
        }

        public String reason() {
            return getFormattedReason();
        }

        public static EligibilityResult eligible(Student student, RecruitmentDrive drive) {
            return new EligibilityResult(
                    true,
                    List.of("Student satisfies all academic cutoff, departmental, and deadline criteria."),
                    student.getCgpa(),
                    drive.getMinimumCgpa(),
                    student.getBranch(),
                    drive.getApplicationDeadline()
            );
        }

        public static EligibilityResult ineligible(List<String> failureReasons, Student student, RecruitmentDrive drive) {
            return new EligibilityResult(
                    false,
                    failureReasons,
                    student.getCgpa(),
                    drive.getMinimumCgpa(),
                    student.getBranch(),
                    drive.getApplicationDeadline()
            );
        }
    }

    /**
     * Evaluates academic criteria only (active status, CGPA cutoff, allowed branches),
     * omitting deadline and closure state checks.
     */
    public EligibilityResult checkAcademicEligibility(Student student, RecruitmentDrive drive) {
        if (student == null) {
            throw new ValidationException("Student cannot be null for eligibility evaluation.");
        }
        if (drive == null) {
            throw new ValidationException("RecruitmentDrive cannot be null for eligibility evaluation.");
        }

        List<String> violations = new ArrayList<>();

        // 1. Enrollment and active status
        if (!student.isActive()) {
            violations.add("Student is currently marked inactive or suspended from campus recruitment.");
        }

        // 2. Minimum CGPA cutoff
        if (!drive.isCgpaEligible(student.getCgpa())) {
            violations.add(String.format("Student CGPA (%.2f) does not meet minimum cutoff of %.2f.",
                    student.getCgpa(), drive.getMinimumCgpa()));
        }

        // 3. Department / branch authorization
        if (!drive.isBranchEligible(student.getBranch())) {
            violations.add(String.format("Student branch '%s' is not in allowed branches %s.",
                    student.getBranch(), drive.getEligibleBranches()));
        }

        if (violations.isEmpty()) {
            return EligibilityResult.eligible(student, drive);
        } else {
            return EligibilityResult.ineligible(violations, student, drive);
        }
    }

    /**
     * Evaluates whether a student meets criteria for a recruitment drive as of current system date.
     */
    public EligibilityResult checkEligibility(Student student, RecruitmentDrive drive) {
        return checkEligibility(student, drive, LocalDate.now());
    }

    /**
     * Evaluates eligibility against a specific reference date (useful for boundary testing).
     */
    public EligibilityResult checkEligibility(Student student, RecruitmentDrive drive, LocalDate evaluationDate) {
        EligibilityResult academic = checkAcademicEligibility(student, drive);
        List<String> violations = new ArrayList<>();
        if (!academic.isEligible()) {
            violations.addAll(academic.reasons());
        }

        LocalDate date = evaluationDate != null ? evaluationDate : LocalDate.now();

        // 4. Drive closure and application deadline
        if (drive.isClosed()) {
            violations.add("Recruitment drive has been closed for applications.");
        }
        if (drive.isDeadlinePassed(date)) {
            violations.add(String.format("Application deadline (%s) has passed as of %s.",
                    drive.getApplicationDeadline(), date));
        }

        if (violations.isEmpty()) {
            return EligibilityResult.eligible(student, drive);
        } else {
            return EligibilityResult.ineligible(violations, student, drive);
        }
    }

    /**
     * Filters a list of students to return only those eligible for the given drive.
     */
    public List<Student> filterEligibleStudents(List<Student> students, RecruitmentDrive drive) {
        if (students == null || drive == null) return List.of();

        List<Student> eligible = new ArrayList<>();
        for (Student student : students) {
            if (checkEligibility(student, drive).isEligible()) {
                eligible.add(student);
            }
        }
        return Collections.unmodifiableList(eligible);
    }
}
