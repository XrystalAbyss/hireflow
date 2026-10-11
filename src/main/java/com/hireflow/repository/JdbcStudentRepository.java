package com.hireflow.repository;

import com.hireflow.db.DataSourceProvider;
import com.hireflow.db.TransactionManager;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.model.Student;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL JDBC implementation of {@link StudentRepository}.
 * Manages university student records, email lookups, branch filtering, and academic validation.
 */
public class JdbcStudentRepository extends AbstractJdbcRepository<Student, String> implements StudentRepository {

    public JdbcStudentRepository(DataSource dataSource) {
        super(dataSource, Student::getId, "Student");
    }

    public JdbcStudentRepository(DataSourceProvider provider) {
        super(provider, Student::getId, "Student");
    }

    public JdbcStudentRepository(TransactionManager transactionManager) {
        super(transactionManager, Student::getId, "Student");
    }

    @Override
    public Student add(Student student) {
        validateEntityForWrite(student, "add");
        String sql = "INSERT INTO students (id, name, email, branch, cgpa, graduation_year, active, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, student.getId());
            ps.setString(2, student.getName());
            ps.setString(3, student.getEmail());
            ps.setString(4, student.getBranch());
            ps.setBigDecimal(5, BigDecimal.valueOf(student.getCgpa()));
            ps.setInt(6, student.getGraduationYear());
            ps.setBoolean(7, student.isActive());
            ps.executeUpdate();
            return student;
        } catch (SQLException ex) {
            throw translateException("Insert", ex);
        }
    }

    @Override
    public Student update(Student student) {
        validateEntityForWrite(student, "update");
        String sql = "UPDATE students SET name = ?, email = ?, branch = ?, cgpa = ?, " +
                "graduation_year = ?, active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, student.getName());
            ps.setString(2, student.getEmail());
            ps.setString(3, student.getBranch());
            ps.setBigDecimal(4, BigDecimal.valueOf(student.getCgpa()));
            ps.setInt(5, student.getGraduationYear());
            ps.setBoolean(6, student.isActive());
            ps.setString(7, student.getId());

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new EntityNotFoundException("Student with ID '" + student.getId() + "' was not found.");
            }
            return student;
        } catch (SQLException ex) {
            throw translateException("Update", ex);
        }
    }

    @Override
    public Student save(Student student) {
        validateEntityForWrite(student, "save");
        String sql = "INSERT INTO students (id, name, email, branch, cgpa, graduation_year, active, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                "ON CONFLICT (id) DO UPDATE SET " +
                "name = EXCLUDED.name, " +
                "email = EXCLUDED.email, " +
                "branch = EXCLUDED.branch, " +
                "cgpa = EXCLUDED.cgpa, " +
                "graduation_year = EXCLUDED.graduation_year, " +
                "active = EXCLUDED.active, " +
                "updated_at = CURRENT_TIMESTAMP";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, student.getId());
            ps.setString(2, student.getName());
            ps.setString(3, student.getEmail());
            ps.setString(4, student.getBranch());
            ps.setBigDecimal(5, BigDecimal.valueOf(student.getCgpa()));
            ps.setInt(6, student.getGraduationYear());
            ps.setBoolean(7, student.isActive());
            ps.executeUpdate();
            return student;
        } catch (SQLException ex) {
            throw translateException("Save", ex);
        }
    }

    @Override
    public Optional<Student> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        String sql = "SELECT id, name, email, branch, cgpa, graduation_year, active FROM students WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.trim().toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException ex) {
            throw translateException("Find by ID", ex);
        }
    }

    @Override
    public List<Student> findAll() {
        String sql = "SELECT id, name, email, branch, cgpa, graduation_year, active FROM students ORDER BY id ASC";
        List<Student> list = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return Collections.unmodifiableList(list);
        } catch (SQLException ex) {
            throw translateException("Find all", ex);
        }
    }

    @Override
    public boolean deleteById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        String sql = "DELETE FROM students WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.trim().toUpperCase());
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw translateException("Delete by ID", ex);
        }
    }

    @Override
    public boolean existsById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        String sql = "SELECT 1 FROM students WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.trim().toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw translateException("Exists by ID", ex);
        }
    }

    @Override
    public int count() {
        String sql = "SELECT COUNT(*) FROM students";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException ex) {
            throw translateException("Count", ex);
        }
    }

    @Override
    public void clear() {
        ensureClearAllowed();
        String sql = "DELETE FROM students";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw translateException("Clear", ex);
        }
    }

    @Override
    public Optional<Student> findByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return Optional.empty();
        }
        String sql = "SELECT id, name, email, branch, cgpa, graduation_year, active FROM students WHERE LOWER(email) = LOWER(?) LIMIT 1";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException ex) {
            throw translateException("Find by email", ex);
        }
    }

    @Override
    public List<Student> findByBranch(String branch) {
        if (branch == null || branch.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String sql = "SELECT id, name, email, branch, cgpa, graduation_year, active FROM students " +
                "WHERE UPPER(branch) = UPPER(?) ORDER BY id ASC";
        List<Student> list = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, branch.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return Collections.unmodifiableList(list);
        } catch (SQLException ex) {
            throw translateException("Find by branch", ex);
        }
    }

    private Student mapRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String name = rs.getString("name");
        String email = rs.getString("email");
        String branch = rs.getString("branch");
        BigDecimal rawCgpa = rs.getBigDecimal("cgpa");
        double cgpa = rawCgpa != null ? rawCgpa.doubleValue() : 0.0;
        int gradYear = rs.getInt("graduation_year");
        boolean active = rs.getBoolean("active");
        return new Student(id, name, email, branch, cgpa, gradYear, active);
    }
}
