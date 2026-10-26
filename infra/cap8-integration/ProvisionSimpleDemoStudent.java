import com.hireflow.web.security.PasswordHasher;
import java.sql.*;
import java.util.Arrays;
import java.util.UUID;

/** Provisions only the fictional Student login in the isolated CAP-8 demo database. */
public final class ProvisionSimpleDemoStudent {
    private static final String URL = "jdbc:postgresql://127.0.0.1:55432/hireflow_cap8_it";
    private static final String USER = "hireflow_cap8_app";
    private static final String STUDENT_ID = "DEMO-STU-LOGIN";
    public static void main(String[] args) throws Exception {
        String dbPassword = System.getenv("HIREFLOW_IT_APP_PASSWORD");
        String password = System.getenv("HIREFLOW_SIMPLE_STUDENT_PASSWORD");
        if (dbPassword == null || dbPassword.isEmpty() || password == null || password.isEmpty())
            throw new IllegalStateException("Isolated DB and demo account secrets are required.");
        char[] chars = password.toCharArray();
        try (Connection c = DriverManager.getConnection(URL, USER, dbPassword)) {
            c.setAutoCommit(false);
            try {
                try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT current_database(), current_user, rolsuper, rolcreatedb, rolcreaterole, " +
                    "(SELECT marker_value FROM public._integration_sentinel WHERE marker_key='ENVIRONMENT_ID') " +
                    "FROM pg_roles WHERE rolname=current_user")) {
                    if (!rs.next() || !"hireflow_cap8_it".equals(rs.getString(1)) || !USER.equals(rs.getString(2)) ||
                        rs.getBoolean(3) || rs.getBoolean(4) || rs.getBoolean(5) ||
                        !"HIREFLOW_CAP8_ISOLATED_INTEGRATION_2026".equals(rs.getString(6)))
                        throw new IllegalStateException("Isolated database identity check failed.");
                }
                try (PreparedStatement st = c.prepareStatement("SELECT id,role,student_id FROM user_accounts WHERE lower(username)=lower(?)")) {
                    st.setString(1, "student");
                    try (ResultSet rs = st.executeQuery()) {
                        if (rs.next() && (!"STUDENT".equals(rs.getString(2)) || !STUDENT_ID.equals(rs.getString(3))))
                            throw new IllegalStateException("Username already belongs to another identity; refusing overwrite.");
                    }
                }
                try (PreparedStatement st = c.prepareStatement("INSERT INTO students (id,name,email,branch,cgpa,graduation_year,active) VALUES (?,?,?,?,?,?,TRUE) ON CONFLICT (id) DO NOTHING")) {
                    st.setString(1, STUDENT_ID); st.setString(2, "Student"); st.setString(3, "student.login@demo.hireflow.edu");
                    st.setString(4, "CSE"); st.setBigDecimal(5, new java.math.BigDecimal("8.75")); st.setInt(6, 2026); st.executeUpdate();
                }
                String hash = new PasswordHasher().hashPassword(chars);
                try (PreparedStatement st = c.prepareStatement(
                    "INSERT INTO user_accounts (id,username,password_hash,role,student_id,display_name,enabled,failed_login_attempts) " +
                    "VALUES (?,?,?,?,?,?,TRUE,0) ON CONFLICT (username) DO UPDATE SET password_hash=EXCLUDED.password_hash, " +
                    "enabled=TRUE, failed_login_attempts=0, locked_until=NULL, updated_at=CURRENT_TIMESTAMP")) {
                    st.setString(1, UUID.randomUUID().toString()); st.setString(2, "student"); st.setString(3, hash);
                    st.setString(4, "STUDENT"); st.setString(5, STUDENT_ID); st.setString(6, "Student"); st.executeUpdate();
                }
                c.commit();
                System.out.println("PROVISIONED student role=STUDENT studentId=" + STUDENT_ID + " database=hireflow_cap8_it port=55432");
            } catch (Exception e) { c.rollback(); throw e; }
        } finally { Arrays.fill(chars, '\0'); }
    }
}

