package com.hireflow.infra;

import com.hireflow.web.security.PasswordHasher;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

/**
 * Isolated test/E2E fixture seeder for HireFlow.
 * Invariants:
 *  - Enforces fail-closed isolation check on target database.
 *  - Reads test passwords from environment/system properties, or generates ephemeral secure tokens.
 *  - Never embeds static credentials in source code.
 *  - Non-destructive: uses ON CONFLICT DO NOTHING to preserve user edits, drive closures, and manual allocations.
 */
public class SeedIsolatedData {

    private static final String ALLOWED_URL = "jdbc:postgresql://127.0.0.1:55432/hireflow_cap8_it";
    private static final String ALLOWED_USER = "hireflow_cap8_app";
    private static final String ALLOWED_DB = "hireflow_cap8_it";
    private static final String SENTINEL = "HIREFLOW_CAP8_ISOLATED_INTEGRATION_2026";

    public static void main(String[] args) throws Exception {
        String url = System.getProperty("hireflow.it.url", ALLOWED_URL);
        String user = System.getProperty("hireflow.it.user", ALLOWED_USER);
        String pass = System.getProperty("hireflow.it.pass");
        if (pass == null || pass.isBlank()) {
            pass = System.getenv("HIREFLOW_IT_APP_PASSWORD");
        }
        if (pass == null || pass.isBlank()) {
            throw new IllegalStateException("Database password not provided. Set HIREFLOW_IT_APP_PASSWORD or pass -Dhireflow.it.pass");
        }

        // Hard allowlist before any network connection
        if (!ALLOWED_URL.equals(url) || !ALLOWED_USER.equals(user)) {
            throw new SecurityException("Refusing to seed: target " + url + " as " + user + " is not the authorized isolated integration database.");
        }

        System.out.println("[Seeder] Connecting to isolated database: " + url + " as " + user);
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            conn.setAutoCommit(false);
            assertIsolation(conn);

            // 1. Student (idempotent, non-destructive)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO students (id, name, email, branch, cgpa, graduation_year, active) " +
                    "VALUES ('E2E-STU-01', 'Alan Turing', 'alan.turing@e2e.edu', 'CSE', 9.50, 2026, TRUE) " +
                    "ON CONFLICT (id) DO NOTHING;")) {
                ps.executeUpdate();
            }

            // 2. User Accounts (read from env or generate secure random tokens)
            String officerSecret = resolveSecret("hireflow.e2e.officer.password", "HIREFLOW_E2E_OFFICER_PASSWORD");
            String studentSecret = resolveSecret("hireflow.e2e.student.password", "HIREFLOW_E2E_STUDENT_PASSWORD");

            PasswordHasher hasher = new PasswordHasher();
            char[] officerChars = officerSecret.toCharArray();
            char[] studentChars = studentSecret.toCharArray();
            String officerHash = hasher.hashPassword(officerChars);
            String studentHash = hasher.hashPassword(studentChars);
            java.util.Arrays.fill(officerChars, '\0');
            java.util.Arrays.fill(studentChars, '\0');

            // Officer Account (idempotent, preserves existing account state)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO user_accounts (id, username, password_hash, role, student_id, display_name, enabled, failed_login_attempts) " +
                    "VALUES (?, 'e2e.officer', ?, 'PLACEMENT_OFFICER', NULL, 'E2E Placement Officer', TRUE, 0) " +
                    "ON CONFLICT (username) DO NOTHING;")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, officerHash);
                ps.executeUpdate();
            }

            // Student Account (idempotent, preserves existing account state)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO user_accounts (id, username, password_hash, role, student_id, display_name, enabled, failed_login_attempts) " +
                    "VALUES (?, 'e2e.student', ?, 'STUDENT', 'E2E-STU-01', 'Alan Turing', TRUE, 0) " +
                    "ON CONFLICT (username) DO NOTHING;")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, studentHash);
                ps.executeUpdate();
            }

            // 3. Company
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO companies (id, name, industry) " +
                    "VALUES ('CMP-101', 'Acme Systems', 'Software Engineering') " +
                    "ON CONFLICT (id) DO NOTHING;")) {
                ps.executeUpdate();
            }

            // 4. Recruitment Drive (idempotent, preserves drive closure settings)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO recruitment_drives (id, company_id, job_title, minimum_cgpa, application_deadline, is_closed) " +
                    "VALUES ('DRV-101', 'CMP-101', 'Software Development Engineer - I (SDE-1)', 7.00, ?, FALSE) " +
                    "ON CONFLICT (id) DO NOTHING;")) {
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
                    "ON CONFLICT (id) DO NOTHING;")) {
                ps.executeUpdate();
            }

            // 6. Room (idempotent, preserves manual capacity/name modifications)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO rooms (id, name, capacity) " +
                    "VALUES ('RM-101', 'Interview Room 101', 4) " +
                    "ON CONFLICT (id) DO NOTHING;")) {
                ps.executeUpdate();
            }

            conn.commit();
            System.out.println("[Seeder] Successfully seeded fictional test records non-destructively in isolated database.");
        }
    }

    private static void assertIsolation(Connection conn) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT current_database(), current_user, rolsuper, rolcreatedb, rolcreaterole, " +
                     "(SELECT marker_value FROM public._integration_sentinel WHERE marker_key='ENVIRONMENT_ID') " +
                     "FROM pg_roles WHERE rolname = current_user")) {
            if (!rs.next()) {
                throw new IllegalStateException("Isolation check could not resolve the current database role.");
            }
            String database = rs.getString(1);
            String currentUser = rs.getString(2);
            boolean superUser = rs.getBoolean(3);
            boolean createDb = rs.getBoolean(4);
            boolean createRole = rs.getBoolean(5);
            String sentinel = rs.getString(6);

            if (!ALLOWED_DB.equals(database) || !ALLOWED_USER.equals(currentUser)) {
                throw new SecurityException("Isolation check failed: target is " + database + " as " + currentUser + ".");
            }
            if (superUser || createDb || createRole) {
                throw new SecurityException("Isolation check failed: application role exceeds least-privilege bounds.");
            }
            if (!SENTINEL.equals(sentinel)) {
                throw new SecurityException("Isolation check failed: required sentinel marker is absent or mismatched.");
            }
        }
    }

    private static String resolveSecret(String propKey, String envKey) {
        String val = System.getProperty(propKey);
        if (val == null || val.isBlank()) {
            val = System.getenv(envKey);
        }
        if (val != null && !val.isBlank()) {
            return val;
        }
        // Generate ephemeral random secret if not supplied
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return "E2E-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
