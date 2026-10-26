package com.hireflow.infra;

import com.hireflow.web.security.PasswordHasher;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

public class SeedIsolatedData {

    public static void main(String[] args) throws Exception {
        String url = System.getProperty("hireflow.it.url", "jdbc:postgresql://127.0.0.1:55432/hireflow_cap8_it");
        String user = System.getProperty("hireflow.it.user", "hireflow_cap8_app");
        String pass = System.getProperty("hireflow.it.pass");
        if (pass == null || pass.isBlank()) {
            pass = System.getenv("HIREFLOW_IT_APP_PASSWORD");
        }
        if (pass == null || pass.isBlank()) {
            throw new IllegalStateException("Database password not provided. Set HIREFLOW_IT_APP_PASSWORD or pass -Dhireflow.it.pass");
        }

        System.out.println("[Seeder] Connecting to isolated database: " + url + " as " + user);
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            conn.setAutoCommit(false);

            // 1. Student
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO students (id, name, email, branch, cgpa, graduation_year, active) " +
                    "VALUES ('E2E-STU-01', 'Alan Turing', 'alan.turing@e2e.edu', 'CSE', 9.50, 2026, TRUE) " +
                    "ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, cgpa = EXCLUDED.cgpa;")) {
                ps.executeUpdate();
            }

            // 2. User Accounts
            PasswordHasher hasher = new PasswordHasher();
            String officerHash = hasher.hashPassword("OfficerPass123!".toCharArray());
            String studentHash = hasher.hashPassword("StudentPass123!".toCharArray());

            // Officer
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO user_accounts (id, username, password_hash, role, student_id, display_name, enabled, failed_login_attempts) " +
                    "VALUES (?, 'e2e.officer', ?, 'PLACEMENT_OFFICER', NULL, 'E2E Placement Officer', TRUE, 0) " +
                    "ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash;")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, officerHash);
                ps.executeUpdate();
            }

            // Student
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO user_accounts (id, username, password_hash, role, student_id, display_name, enabled, failed_login_attempts) " +
                    "VALUES (?, 'e2e.student', ?, 'STUDENT', 'E2E-STU-01', 'Alan Turing', TRUE, 0) " +
                    "ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash;")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, studentHash);
                ps.executeUpdate();
            }

            // 3. Company
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO companies (id, name, industry) " +
                    "VALUES ('CMP-101', 'Acme Systems', 'Software Engineering') " +
                    "ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;")) {
                ps.executeUpdate();
            }

            // 4. Recruitment Drive
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO recruitment_drives (id, company_id, job_title, minimum_cgpa, application_deadline, is_closed) " +
                    "VALUES ('DRV-101', 'CMP-101', 'Software Development Engineer - I (SDE-1)', 7.00, ?, FALSE) " +
                    "ON CONFLICT (id) DO UPDATE SET job_title = EXCLUDED.job_title, is_closed = FALSE;")) {
                ps.setDate(1, Date.valueOf(LocalDate.of(2027, 12, 31)));
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO drive_eligible_branches (drive_id, branch) " +
                    "VALUES ('DRV-101', 'CSE') ON CONFLICT DO NOTHING;")) {
                ps.executeUpdate();
            }

            // 5. Interviewer
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO interviewers (id, name, company_id) " +
                    "VALUES ('INT-101', 'Dr. Grace Hopper', 'CMP-101') " +
                    "ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;")) {
                ps.executeUpdate();
            }

            // 6. Room
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO rooms (id, name, capacity) " +
                    "VALUES ('RM-101', 'Interview Room 101', 4) " +
                    "ON CONFLICT (id) DO UPDATE SET capacity = EXCLUDED.capacity;")) {
                ps.executeUpdate();
            }

            conn.commit();
            System.out.println("[Seeder] Successfully seeded fictional test records in isolated database.");
        }
    }
}
