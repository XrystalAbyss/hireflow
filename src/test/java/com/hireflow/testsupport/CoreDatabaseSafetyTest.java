package com.hireflow.testsupport;

import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoreDatabaseSafetyTest {
    @Test void rejectsHostPortOrBootstrapRoleBeforeAnySql() {
        assertThrows(IllegalStateException.class,()->SafeTestDatabase.requireTarget("jdbc:postgresql://127.0.0.1:5432/hireflow_test","hireflow_cap8_app"));
        assertThrows(IllegalStateException.class,()->SafeTestDatabase.requireTarget(CoreTestDatabase.url(),"postgres"));
    }
    @Test void rejectsPublicSchemaFallbackBeforeQueryingOrDeleting() throws Exception {
        Connection c=mock(Connection.class);DatabaseMetaData metadata=mock(DatabaseMetaData.class);
        when(c.getMetaData()).thenReturn(metadata);when(metadata.getURL()).thenReturn(CoreTestDatabase.url());
        when(metadata.getUserName()).thenReturn("hireflow_cap8_app");when(c.getSchema()).thenReturn("public");
        assertThrows(IllegalStateException.class,()->SafeTestDatabase.verify(c));
        verify(c,never()).createStatement();verify(c,never()).prepareStatement(anyString());
    }
    @Test void rejectsMissingIntegrationSentinel() throws Exception {
        Connection c=mock(Connection.class);DatabaseMetaData metadata=mock(DatabaseMetaData.class);
        when(c.getMetaData()).thenReturn(metadata);when(metadata.getURL()).thenReturn(CoreTestDatabase.url());
        when(metadata.getUserName()).thenReturn("hireflow_cap8_app");when(c.getSchema()).thenReturn(CoreTestDatabase.schema());
        Statement statement=mock(Statement.class);ResultSet result=mock(ResultSet.class);
        when(c.createStatement()).thenReturn(statement);when(statement.executeQuery(anyString())).thenReturn(result);
        when(result.next()).thenReturn(false);
        assertThrows(IllegalStateException.class,()->SafeTestDatabase.verify(c));
        verify(c,never()).prepareStatement(anyString());
    }
}
