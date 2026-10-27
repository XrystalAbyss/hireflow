package com.hireflow.api.testsupport;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

/** Test-only isolation checks and transactional deletion of explicitly enumerated fixtures. */
public final class SafeTestDatabase {
    public static final String URL = "jdbc:postgresql://127.0.0.1:55432/hireflow_cap8_it";
    public static final String USER = "hireflow_cap8_app";
    private static final Map<String, Set<String>> protectedIds = new HashMap<>();
    private static final List<String> TABLES = List.of("interview_sessions", "interviewer_availabilities",
        "applications", "user_accounts", "drive_eligible_branches", "interviewers", "rooms", "recruitment_drives", "companies", "students");
    private static boolean captured;
    private SafeTestDatabase() {}

    public static void requireTarget(String url, String user) {
        if (!URL.equals(url) || !USER.equals(user))
            throw new IllegalStateException("Unsafe test database connection target rejected before connection");
    }

    public static void verify(Connection c) throws SQLException {
        requireTarget(c.getMetaData().getURL(), c.getMetaData().getUserName());
        try (var st = c.createStatement(); var rs = st.executeQuery("SELECT current_database(), current_user, marker_value, rolsuper, rolcreatedb, rolcreaterole, rolreplication, rolbypassrls FROM public._integration_sentinel CROSS JOIN pg_roles WHERE marker_key='ENVIRONMENT_ID' AND rolname=current_user")) {
            if (!rs.next() || !"hireflow_cap8_it".equals(rs.getString(1)) || !USER.equals(rs.getString(2))
                || !"HIREFLOW_CAP8_ISOLATED_INTEGRATION_2026".equals(rs.getString(3)))
                throw new IllegalStateException("Test database sentinel or identity mismatch");
            for (int i=4;i<=8;i++) if(rs.getBoolean(i)) throw new IllegalStateException("Test database role is privileged");
        }
    }

    public static synchronized void captureBaseline(Connection c) throws SQLException {
        verify(c);
        if (captured) return;
        for (String table : TABLES) {
            if (table.equals("drive_eligible_branches")) continue;
            var ids = new HashSet<String>();
            try (var st = c.createStatement(); var rs = st.executeQuery("SELECT id FROM " + table)) {
                while (rs.next()) ids.add(rs.getString(1));
            } catch (SQLException ignored) {
                // Table may not exist yet if migrations run during Spring Boot startup
            }
            protectedIds.put(table, ids);
        }
        captured=true;
    }

    public static void clean(DataSource ds, String[] ids, String[] usernames) {
        if (ids.length==0 && usernames.length==0) throw new IllegalArgumentException("Empty fixture scope");
        try (var c=ds.getConnection()) {
            verify(c);
            if (!captured) throw new IllegalStateException("Test baseline was not captured before fixture writes");
            c.setAutoCommit(false);
            try {
                String apps="SELECT id FROM applications WHERE id=ANY(?) OR drive_id=ANY(?) OR student_id=ANY(?)";
                delete(c,"interview_sessions","application_id IN ("+apps+")",ids,ids,ids);
                delete(c,"interviewer_availabilities","interviewer_id=ANY(?)",ids);
                delete(c,"applications","id=ANY(?) OR drive_id=ANY(?) OR student_id=ANY(?)",ids,ids,ids);
                delete(c,"user_accounts","username=ANY(?) OR student_id=ANY(?)",usernames,ids);
                // Branch rows have a composite key; protect their owning drive instead.
                for(String id:ids) if(protectedIds.get("recruitment_drives").contains(id))
                    throw new IllegalStateException("Refusing cleanup of a pre-existing drive: "+id);
                delete(c,"drive_eligible_branches","drive_id=ANY(?)",ids);
                for(String table:List.of("interviewers","rooms","recruitment_drives","companies","students"))
                    delete(c,table,"id=ANY(?)",ids);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw new IllegalStateException("Scoped test cleanup rolled back",e);
            }
        } catch (SQLException e) { throw new IllegalStateException("Scoped test cleanup failed",e); }
    }

    private static void delete(Connection c,String table,String where,String[]... values) throws SQLException {
        if (!TABLES.contains(table)) throw new IllegalArgumentException("Unknown cleanup table");
        if (!table.equals("drive_eligible_branches")) {
            try(var ps=c.prepareStatement("SELECT id FROM "+table+" WHERE "+where+" FOR UPDATE")) {
                bind(c,ps,values);
                try(var rs=ps.executeQuery()) { while(rs.next()) if(protectedIds.get(table).contains(rs.getString(1)))
                    throw new IllegalStateException("Refusing cleanup of pre-existing "+table+" row "+rs.getString(1)); }
            }
        }
        try(var ps=c.prepareStatement("DELETE FROM "+table+" WHERE "+where)) { bind(c,ps,values); ps.executeUpdate(); }
    }
    private static void bind(Connection c,PreparedStatement ps,String[][] values) throws SQLException {
        for(int i=0;i<values.length;i++) ps.setArray(i+1,c.createArrayOf("varchar",values[i]));
    }
}
