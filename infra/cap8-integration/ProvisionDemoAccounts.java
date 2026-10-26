import com.hireflow.web.security.PasswordHasher;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One-time local demo account provisioning for the authorized, isolated HireFlow
 * CAP-8.5.2 integration database.
 *
 * Safety invariants:
 *  - Refuses to run unless the exact isolated allowlist is confirmed by live query.
 *  - Never accepts passwords as command-line arguments; reads them from the process
 *    environment supplied by the caller and never echoes them.
 *  - Reuses the application's legitimate BCrypt PasswordHasher; stores only hashes.
 *  - Idempotent: re-running refreshes the hash and re-enables the account.
 *
 * Required environment variables (via -Dhireflow.it.* system properties or env):
 *   HIREFLOW_IT_APP_PASSWORD          isolated application-role password (required)
 *   HIREFLOW_DEMO_MASTER_PASSWORD     demo.master password (required)
 *   HIREFLOW_DEMO_OFFICER_PASSWORD    demo.officer password (required)
 *   HIREFLOW_DEMO_STUDENT_PASSWORD    demo.student password (required)
 */
public final class ProvisionDemoAccounts {

    private static final String DB_URL = "jdbc:postgresql://127.0.0.1:55432/hireflow_cap8_it";
    private static final String DB_USER = "hireflow_cap8_app";
    private static final String DB_NAME = "hireflow_cap8_it";
    private static final String SENTINEL = "HIREFLOW_CAP8_ISOLATED_INTEGRATION_2026";

    private static final String DEMO_STUDENT_ID = "DEMO-STU-01";

    private ProvisionDemoAccounts() {
    }

    public static void main(String[] args) throws Exception {
        String url = System.getProperty("hireflow.it.url", DB_URL);
        String user = System.getProperty("hireflow.it.user", DB_USER);
        String dbPass = require("HIREFLOW_IT_APP_PASSWORD");
        String masterPass = require("HIREFLOW_DEMO_MASTER_PASSWORD");
        String officerPass = require("HIREFLOW_DEMO_OFFICER_PASSWORD");
        String studentPass = require("HIREFLOW_DEMO_STUDENT_PASSWORD");

        // Hard allowlist before any network activity.
        if (!DB_URL.equals(url) || !DB_USER.equals(user)) {
            fail("Provisioning target is outside the exact authorized isolated allowlist.");
        }

        PasswordHasher hasher = new PasswordHasher();
        char[] masterChars = masterPass.toCharArray();
        char[] officerChars = officerPass.toCharArray();
        char[] studentChars = studentPass.toCharArray();

        try (Connection conn = DriverManager.getConnection(url, user, dbPass)) {
            conn.setAutoCommit(false);
            assertIsolation(conn);

            upsertDemoStudent(conn);

            Map<String, char[]> accounts = new LinkedHashMap<>();
            accounts.put("demo.master|ADMINISTRATOR||HireFlow Demo Administrator", masterChars);
            accounts.put("demo.officer|PLACEMENT_OFFICER||HireFlow Demo Placement Officer", officerChars);
            accounts.put("demo.student|STUDENT|" + DEMO_STUDENT_ID + "|HireFlow Demo Student", studentChars);

            for (Map.Entry<String, char[]> entry : accounts.entrySet()) {
                String[] meta = entry.getKey().split("\\|", -1);
                String hash = hasher.hashPassword(entry.getValue());
                upsertAccount(conn, meta[0], hash, meta[1],
                        meta[2].isEmpty() ? null : meta[2], meta[3]);
                System.out.println("PROVISIONED " + meta[0] + " role=" + meta[1]);
            }

            conn.commit();
            System.out.println("PROVISIONING_COMPLETE accounts=3 database=" + DB_NAME + " host=127.0.0.1 port=55432");
        } finally {
            java.util.Arrays.fill(masterChars, '\0');
            java.util.Arrays.fill(officerChars, '\0');
            java.util.Arrays.fill(studentChars, '\0');
        }
    }

    private static void assertIsolation(Connection conn) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT current_database(), current_user, rolsuper, rolcreatedb, rolcreaterole, "
                             + "(SELECT marker_value FROM public._integration_sentinel WHERE marker_key='ENVIRONMENT_ID') "
                             + "FROM pg_roles WHERE rolname = current_user")) {
            if (!rs.next()) {
                fail("Isolation check could not resolve the current role.");
            }
            String database = rs.getString(1);
            String currentUser = rs.getString(2);
            boolean superUser = rs.getBoolean(3);
            boolean createDb = rs.getBoolean(4);
            boolean createRole = rs.getBoolean(5);
            String sentinel = rs.getString(6);

            if (!DB_NAME.equals(database) || !DB_USER.equals(currentUser)) {
                fail("Isolation check failed: connected to " + database + " as " + currentUser + ".");
            }
            if (superUser || createDb || createRole) {
                fail("Isolation check failed: application role exceeds least-privilege bounds.");
            }
            if (!SENTINEL.equals(sentinel)) {
                fail("Isolation check failed: required sentinel marker is absent or mismatched.");
            }
        }
    }

    private static void upsertDemoStudent(Connection conn) throws Exception {
        String sql = "INSERT INTO students (id, name, email, branch, cgpa, graduation_year, active) "
                + "VALUES (?, ?, ?, ?, ?, ?, TRUE) "
                + "ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, email = EXCLUDED.email, "
                + "branch = EXCLUDED.branch, cgpa = EXCLUDED.cgpa, graduation_year = EXCLUDED.graduation_year, "
                + "active = TRUE, updated_at = CURRENT_TIMESTAMP";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, DEMO_STUDENT_ID);
            ps.setString(2, "Aarav Sharma");
            ps.setString(3, "aarav.sharma@demo.hireflow.edu");
            ps.setString(4, "CSE");
            ps.setBigDecimal(5, new java.math.BigDecimal("8.75"));
            ps.setInt(6, 2026);
            ps.executeUpdate();
        }
    }

    private static void upsertAccount(Connection conn, String username, String hash, String role,
                                      String studentId, String displayName) throws Exception {
        String sql = "INSERT INTO user_accounts "
                + "(id, username, password_hash, role, student_id, display_name, enabled, failed_login_attempts) "
                + "VALUES (?, ?, ?, ?, ?, ?, TRUE, 0) "
                + "ON CONFLICT (username) DO UPDATE SET password_hash = EXCLUDED.password_hash, "
                + "role = EXCLUDED.role, student_id = EXCLUDED.student_id, display_name = EXCLUDED.display_name, "
                + "enabled = TRUE, failed_login_attempts = 0, locked_until = NULL, updated_at = CURRENT_TIMESTAMP";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, username);
            ps.setString(3, hash);
            ps.setString(4, role);
            ps.setString(5, studentId);
            ps.setString(6, displayName);
            ps.executeUpdate();
        }
        Timestamp now = Timestamp.from(Instant.now());
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE user_accounts SET updated_at = ? WHERE username = ?")) {
            ps.setTimestamp(1, now);
            ps.setString(2, username);
            ps.executeUpdate();
        }
    }

    private static String require(String name) {
        String value = System.getProperty("hireflow." + name.toLowerCase(), System.getenv(name));
        if (value == null || value.isEmpty()) {
            fail("Missing required secret environment variable: " + name);
        }
        return value;
    }

    private static void fail(String message) {
        System.err.println("ERROR: " + message);
        System.exit(1);
    }
}
