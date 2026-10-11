package com.hireflow.repository;

import com.hireflow.db.DataSourceProvider;
import com.hireflow.db.TransactionManager;
import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.model.Application;
import com.hireflow.model.ApplicationStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL JDBC-backed implementation of {@link ApplicationRepository}.
 * Manages persistence for student drive applications with atomic transactions,
 * database foreign-key validation (students, recruitment_drives), composite unique
 * constraint enforcement (student_id, drive_id), optimistic conditional status updates,
 * and pessimistic row locking.
 */
public class JdbcApplicationRepository extends AbstractJdbcRepository<Application, String> implements ApplicationRepository {

    public JdbcApplicationRepository(DataSource dataSource) {
        super(dataSource, Application::getId, "Application");
    }

    public JdbcApplicationRepository(DataSourceProvider provider) {
        super(provider, Application::getId, "Application");
    }

    public JdbcApplicationRepository(TransactionManager txManager) {
        super(txManager, Application::getId, "Application");
    }

    public JdbcApplicationRepository(DataSource dataSource, TransactionManager txManager) {
        super(dataSource, txManager, Application::getId, "Application");
    }

    @Override
    public Application add(Application application) {
        validateEntityForWrite(application, "add");
        String normId = normalizeId(application.getId());
        String normStudentId = normalizeId(application.getStudentId());
        String normDriveId = normalizeId(application.getDriveId());

        if (existsById(normId)) {
            throw new DuplicateEntityException("Application with ID '" + normId + "' already exists.");
        }
        if (existsByStudentAndDrive(normStudentId, normDriveId)) {
            throw new DuplicateEntityException("Student '" + normStudentId +
                    "' has already applied to Drive '" + normDriveId + "'.");
        }

        String sql = "INSERT INTO applications (id, student_id, drive_id, status, applied_at, remarks, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normId);
            ps.setString(2, normStudentId);
            ps.setString(3, normDriveId);
            ps.setString(4, application.getStatus().name());
            ps.setTimestamp(5, application.getAppliedAt() != null
                    ? Timestamp.valueOf(application.getAppliedAt())
                    : Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(6, application.getRemarks() != null ? application.getRemarks() : "");

            ps.executeUpdate();
            return application;
        } catch (SQLException ex) {
            throw translateException("Add", ex);
        }
    }

    @Override
    public Application update(Application application) {
        validateEntityForWrite(application, "update");
        String normId = normalizeId(application.getId());
        String normStudentId = normalizeId(application.getStudentId());
        String normDriveId = normalizeId(application.getDriveId());

        String sql = "UPDATE applications SET student_id = ?, drive_id = ?, status = ?, applied_at = ?, remarks = ?, " +
                "updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normStudentId);
            ps.setString(2, normDriveId);
            ps.setString(3, application.getStatus().name());
            ps.setTimestamp(4, application.getAppliedAt() != null
                    ? Timestamp.valueOf(application.getAppliedAt())
                    : Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(5, application.getRemarks() != null ? application.getRemarks() : "");
            ps.setString(6, normId);

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new EntityNotFoundException("Application with ID '" + normId + "' was not found.");
            }
            return application;
        } catch (SQLException ex) {
            throw translateException("Update", ex);
        }
    }

    @Override
    public Application save(Application application) {
        validateEntityForWrite(application, "save");
        String normId = normalizeId(application.getId());
        String normStudentId = normalizeId(application.getStudentId());
        String normDriveId = normalizeId(application.getDriveId());

        String sql = "INSERT INTO applications (id, student_id, drive_id, status, applied_at, remarks, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                "ON CONFLICT (id) DO UPDATE SET " +
                "student_id = EXCLUDED.student_id, " +
                "drive_id = EXCLUDED.drive_id, " +
                "status = EXCLUDED.status, " +
                "applied_at = EXCLUDED.applied_at, " +
                "remarks = EXCLUDED.remarks, " +
                "updated_at = CURRENT_TIMESTAMP";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normId);
            ps.setString(2, normStudentId);
            ps.setString(3, normDriveId);
            ps.setString(4, application.getStatus().name());
            ps.setTimestamp(5, application.getAppliedAt() != null
                    ? Timestamp.valueOf(application.getAppliedAt())
                    : Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(6, application.getRemarks() != null ? application.getRemarks() : "");

            ps.executeUpdate();
            return application;
        } catch (SQLException ex) {
            throw translateException("Save", ex);
        }
    }

    @Override
    public Optional<Application> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        String sql = "SELECT id, student_id, drive_id, status, applied_at, remarks FROM applications WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizeId(id));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw translateException("Find by ID", ex);
        }
    }

    @Override
    public Optional<Application> findByIdForUpdate(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        String sql = "SELECT id, student_id, drive_id, status, applied_at, remarks FROM applications WHERE id = ? FOR UPDATE";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizeId(id));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw translateException("Find by ID for update", ex);
        }
    }

    @Override
    public List<Application> findAll() {
        String sql = "SELECT id, student_id, drive_id, status, applied_at, remarks FROM applications ORDER BY id ASC";
        List<Application> list = new ArrayList<>();

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
        String sql = "DELETE FROM applications WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizeId(id));
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
        String sql = "SELECT 1 FROM applications WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizeId(id));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw translateException("Exists by ID", ex);
        }
    }

    @Override
    public int count() {
        String sql = "SELECT COUNT(*) FROM applications";

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
        String sql = "DELETE FROM applications";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw translateException("Clear", ex);
        }
    }

    @Override
    public List<Application> findByStudentId(String studentId) {
        if (studentId == null || studentId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String sql = "SELECT id, student_id, drive_id, status, applied_at, remarks FROM applications " +
                "WHERE student_id = ? ORDER BY applied_at ASC";
        List<Application> list = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizeId(studentId));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return Collections.unmodifiableList(list);
            }
        } catch (SQLException ex) {
            throw translateException("Find by student ID", ex);
        }
    }

    @Override
    public List<Application> findByDriveId(String driveId) {
        if (driveId == null || driveId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String sql = "SELECT id, student_id, drive_id, status, applied_at, remarks FROM applications " +
                "WHERE drive_id = ? ORDER BY applied_at ASC";
        List<Application> list = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizeId(driveId));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return Collections.unmodifiableList(list);
            }
        } catch (SQLException ex) {
            throw translateException("Find by drive ID", ex);
        }
    }

    @Override
    public List<Application> findByStatus(ApplicationStatus status) {
        if (status == null) {
            return Collections.emptyList();
        }
        String sql = "SELECT id, student_id, drive_id, status, applied_at, remarks FROM applications " +
                "WHERE status = ? ORDER BY applied_at ASC";
        List<Application> list = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return Collections.unmodifiableList(list);
            }
        } catch (SQLException ex) {
            throw translateException("Find by status", ex);
        }
    }

    @Override
    public Optional<Application> findByStudentAndDrive(String studentId, String driveId) {
        if (studentId == null || studentId.trim().isEmpty() || driveId == null || driveId.trim().isEmpty()) {
            return Optional.empty();
        }
        String sql = "SELECT id, student_id, drive_id, status, applied_at, remarks FROM applications " +
                "WHERE student_id = ? AND drive_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizeId(studentId));
            ps.setString(2, normalizeId(driveId));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw translateException("Find by student and drive", ex);
        }
    }

    @Override
    public Optional<Application> findByStudentIdAndDriveId(String studentId, String driveId) {
        return findByStudentAndDrive(studentId, driveId);
    }

    @Override
    public boolean existsByStudentAndDrive(String studentId, String driveId) {
        if (studentId == null || studentId.trim().isEmpty() || driveId == null || driveId.trim().isEmpty()) {
            return false;
        }
        String sql = "SELECT 1 FROM applications WHERE student_id = ? AND drive_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizeId(studentId));
            ps.setString(2, normalizeId(driveId));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw translateException("Exists by student and drive", ex);
        }
    }

    @Override
    public boolean updateStatusConditional(String id, ApplicationStatus expectedStatus,
                                           ApplicationStatus newStatus, String remarks) {
        if (id == null || expectedStatus == null || newStatus == null) {
            return false;
        }
        String sql = "UPDATE applications SET status = ?, remarks = ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE id = ? AND status = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus.name());
            ps.setString(2, remarks != null ? remarks : "");
            ps.setString(3, normalizeId(id));
            ps.setString(4, expectedStatus.name());
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw translateException("Update status conditional", ex);
        }
    }

    private Application mapRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String studentId = rs.getString("student_id");
        String driveId = rs.getString("drive_id");
        String statusStr = rs.getString("status");
        ApplicationStatus status = ApplicationStatus.valueOf(statusStr);
        Timestamp appliedTs = rs.getTimestamp("applied_at");
        LocalDateTime appliedAt = appliedTs != null ? appliedTs.toLocalDateTime() : LocalDateTime.now();
        String remarks = rs.getString("remarks");
        return new Application(id, studentId, driveId, status, appliedAt, remarks != null ? remarks : "");
    }
}
