package com.hireflow.testsupport;

import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/** One new, explicitly authorized schema for this core test run; public remains protected. */
public final class CoreTestDatabase {
    private static final List<String> TABLES=List.of("interview_sessions","interviewer_availabilities","applications","user_accounts","interviewers","rooms","recruitment_drives","companies","students");
    private static Map<String,Set<String>> classBaseline;
    private CoreTestDatabase() {}
    public static String schema() {
        String value=System.getenv("HIREFLOW_CORE_TEST_SCHEMA");
        if(value==null || !value.matches("cap852_core_[a-f0-9]{12}")) throw new IllegalStateException("Explicit disposable core schema required");
        return value;
    }
    public static String url() { return "jdbc:postgresql://127.0.0.1:55432/hireflow_cap8_it?currentSchema="+schema()+",public"; }
    public static Connection connect() throws SQLException {
        if(!"55432/hireflow_cap8_it".equals(System.getenv("HIREFLOW_ALLOW_ISOLATED_IT"))) throw new IllegalStateException("Explicit isolated test opt-in required");
        if(!url().equals(System.getenv("HIREFLOW_DB_URL")) || !"hireflow_cap8_app".equals(System.getenv("HIREFLOW_DB_USER"))) throw new IllegalStateException("Core test database target mismatch");
        return DriverManager.getConnection(url(),"hireflow_cap8_app",System.getenv("HIREFLOW_DB_PASSWORD"));
    }
    public static void verifyScope() {
        try(var c=connect()){SafeTestDatabase.verify(c);}catch(SQLException e){throw new IllegalStateException("Core test database guard failed",e);}
    }
    static void beforeClass() throws Exception {
        verifyScope();
        Flyway.configure().dataSource(url(),"hireflow_cap8_app",System.getenv("HIREFLOW_DB_PASSWORD"))
            .schemas(schema()).defaultSchema(schema()).locations("classpath:db/migration").cleanDisabled(true).load().migrate();
        try(var c=connect()){SafeTestDatabase.captureBaseline(c);classBaseline=snapshot(c);}
    }
    static void afterClass() {
        cleanCurrentClass();
        classBaseline=null;
    }
    private static Map<String,Set<String>> snapshot(Connection c) throws SQLException {
        SafeTestDatabase.verify(c);var result=new HashMap<String,Set<String>>();
        for(String table:TABLES){var values=new HashSet<String>();try(var st=c.createStatement();var rs=st.executeQuery("SELECT "+(table.equals("user_accounts")?"username":"id")+" FROM "+schema()+"."+table)){while(rs.next())values.add(rs.getString(1));}result.put(table,values);}
        return result;
    }
    public static synchronized void cleanCurrentClass() {
        if(classBaseline==null)throw new IllegalStateException("Core class ownership baseline not initialized");
        try(var c=connect()) {
            var current=snapshot(c);var ids=new HashSet<String>();var users=new HashSet<String>();
            for(String table:TABLES){var added=new HashSet<>(current.get(table));added.removeAll(classBaseline.get(table));if(table.equals("user_accounts"))users.addAll(added);else ids.addAll(added);}
            if(ids.isEmpty()&&users.isEmpty())return;
            SafeTestDatabase.clean(new DirectDataSource(),ids.toArray(String[]::new),users.toArray(String[]::new));
        }catch(SQLException e){throw new IllegalStateException("Core fixture cleanup failed",e);}
    }
    private static final class DirectDataSource implements DataSource {
        public Connection getConnection()throws SQLException{return connect();}
        public Connection getConnection(String u,String p){throw new UnsupportedOperationException();}
        public java.io.PrintWriter getLogWriter(){return null;}public void setLogWriter(java.io.PrintWriter p){}
        public void setLoginTimeout(int n){}public int getLoginTimeout(){return 0;}
        public java.util.logging.Logger getParentLogger(){return java.util.logging.Logger.getGlobal();}
        public <T>T unwrap(Class<T> c){throw new UnsupportedOperationException();}public boolean isWrapperFor(Class<?> c){return false;}
    }
}
