package com.hireflow.api.testsupport;

import java.sql.*;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SafeTestDatabaseTest {
    @Test void rejectsHostPortAndOtherRolesBeforeConnecting() {
        assertThrows(IllegalStateException.class, () -> SafeTestDatabase.requireTarget("jdbc:postgresql://127.0.0.1:5432/hireflow_test", SafeTestDatabase.USER));
        assertThrows(IllegalStateException.class, () -> SafeTestDatabase.requireTarget(SafeTestDatabase.URL, "postgres"));
        assertThrows(IllegalStateException.class, () -> SafeTestDatabase.requireTarget("jdbc:postgresql://example.org:55432/hireflow_cap8_it", SafeTestDatabase.USER));
    }
    @Test void connectionWithUnexpectedIdentityNeverExecutesSql() throws Exception {
        Connection c=mock(Connection.class);
        DatabaseMetaData metadata=mock(DatabaseMetaData.class);
        when(c.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:postgresql://127.0.0.1:5432/hireflow_test");
        when(metadata.getUserName()).thenReturn("hireflow_user");
        assertThrows(IllegalStateException.class, () -> SafeTestDatabase.verify(c));
        verify(c,never()).createStatement();
    }
    @Test void preExistingRowProtectionRollsBackEarlierFixtureDeletes() throws Exception {
        DataSource ds=new DataSource() {
            public Connection getConnection() throws SQLException { return DriverManager.getConnection(SafeTestDatabase.URL,SafeTestDatabase.USER,System.getenv("HIREFLOW_DB_PASSWORD")); }
            public Connection getConnection(String u,String p) throws SQLException { throw new UnsupportedOperationException(); }
            public java.io.PrintWriter getLogWriter(){return null;} public void setLogWriter(java.io.PrintWriter p){}
            public void setLoginTimeout(int n){} public int getLoginTimeout(){return 0;}
            public java.util.logging.Logger getParentLogger(){return java.util.logging.Logger.getGlobal();}
            public <T>T unwrap(Class<T> c){throw new UnsupportedOperationException();} public boolean isWrapperFor(Class<?> c){return false;}
        };
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,10).toUpperCase();
        String student="STU-SAFE-"+suffix, drive="DRV-SAFE-"+suffix, application="APP-SAFE-"+suffix;
        String company;
        try(var c=ds.getConnection()) {
            SafeTestDatabase.captureBaseline(c);
            try(var st=c.createStatement();var rs=st.executeQuery("SELECT id FROM companies ORDER BY id LIMIT 1")) {
                assertTrue(rs.next(),"Preservation regression requires a pre-existing company in the isolated baseline");company=rs.getString(1);
            }
            try(var ps=c.prepareStatement("INSERT INTO students(id,name,email,branch,cgpa,graduation_year,active) VALUES(?,'Fictional safety regression',?,'CSE',9,2026,true)")) {ps.setString(1,student);ps.setString(2,suffix+"@safe.test");ps.executeUpdate();}
            try(var ps=c.prepareStatement("INSERT INTO recruitment_drives(id,company_id,job_title,minimum_cgpa,application_deadline,is_closed) VALUES(?,?,'Fictional safety regression',7,'2030-01-01',false)")){ps.setString(1,drive);ps.setString(2,company);ps.executeUpdate();}
            try(var ps=c.prepareStatement("INSERT INTO applications(id,student_id,drive_id,status) VALUES(?,?,?,'APPLIED')")){ps.setString(1,application);ps.setString(2,student);ps.setString(3,drive);ps.executeUpdate();}
        }
        try {
            assertThrows(IllegalStateException.class,()->SafeTestDatabase.clean(ds,new String[]{student,drive,application,company},new String[]{}));
            try(var c=ds.getConnection();var ps=c.prepareStatement("SELECT count(*) FROM applications WHERE id=?")){ps.setString(1,application);try(var rs=ps.executeQuery()){rs.next();assertEquals(1,rs.getInt(1),"Earlier application deletion must roll back when protected company cleanup is rejected");}}
        } finally { SafeTestDatabase.clean(ds,new String[]{student,drive,application},new String[]{}); }
    }
}
