package com.hireflow.repository;

import com.hireflow.db.DataSourceProvider;
import com.hireflow.db.TransactionManager;
import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;
import com.hireflow.model.InterviewSession;

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
 * PostgreSQL JDBC-backed implementation of {@link InterviewSessionRepository}.
 * Manages persistence for confirmed interview session appointments with transaction safety,
 * authoritative student identity resolution from applications, composite foreign keys
 * (application_id, student_id), unique application appointment enforcement, and PostgreSQL
 * GiST temporal exclusion constraints for student, interviewer, and room double-booking prevention.
 */
public class JdbcInterviewSessionRepository extends AbstractJdbcRepository<InterviewSession, String> implements InterviewSessionRepository {

    public JdbcInterviewSessionRepository(DataSource dataSource) {
        super(dataSource, InterviewSession::getId, "InterviewSession");
    }

    public JdbcInterviewSessionRepository(DataSourceProvider provider) {
        super(provider, InterviewSession::getId, "InterviewSession");
    }

    public JdbcInterviewSessionRepository(TransactionManager txManager) {
        super(txManager, InterviewSession::getId, "InterviewSession");
    }

    public JdbcInterviewSessionRepository(DataSource dataSource, TransactionManager txManager) {
        super(dataSource, txManager, InterviewSession::getId, "InterviewSession");
    }

    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    @Override
    public InterviewSession add(InterviewSession session) {
        return addWithStudentId(session, null);
    }

    /**
     * Adds an interview session, validating that any caller-supplied student ID matches
     * the authoritative student ID recorded in the applications table.
     */
    public InterviewSession addWithStudentId(InterviewSession session, String candidateStudentId) {
        validateEntityForWrite(session, "add");
        String normId = normalizeId(session.getId());
        String normAppId = normalizeId(session.getApplicationId());
        String normIntId = normalizeId(session.getInterviewerId());
        String normRoomId = normalizeId(session.getRoomId());

        if (existsById(normId)) {
            throw new DuplicateEntityException("InterviewSession with ID '" + normId + "' already exists.");
        }
        if (!findByApplicationId(normAppId).isEmpty()) {
            throw new DuplicateEntityException("Application '" + normAppId + "' already has a scheduled interview session.");
        }

        try (Connection conn = getConnection()) {
            String studentId = resolveStudentId(conn, normAppId);
            if (candidateStudentId != null && !candidateStudentId.trim().equalsIgnoreCase(studentId)) {
                throw new ValidationException(String.format(
                        "Mismatched student ID: candidate '%s' does not match application student '%s'.",
                        candidateStudentId.trim().toUpperCase(), studentId));
            }

            String sql = "INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, " +
                    "start_time, end_time, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, normId);
                ps.setString(2, normAppId);
                ps.setString(3, studentId);
                ps.setString(4, normIntId);
                ps.setString(5, normRoomId);
                ps.setTimestamp(6, Timestamp.valueOf(session.getStartTime()));
                ps.setTimestamp(7, Timestamp.valueOf(session.getEndTime()));
                ps.executeUpdate();
            }
            return session;
        } catch (SQLException ex) {
            throw translateException("Add", ex);
        }
    }

    @Override
    public InterviewSession update(InterviewSession session) {
        validateEntityForWrite(session, "update");
        String normId = normalizeId(session.getId());
        String normAppId = normalizeId(session.getApplicationId());
        String normIntId = normalizeId(session.getInterviewerId());
        String normRoomId = normalizeId(session.getRoomId());

        try (Connection conn = getConnection()) {
            String studentId = resolveStudentId(conn, normAppId);

            String sql = "UPDATE interview_sessions " +
                    "SET application_id = ?, student_id = ?, interviewer_id = ?, room_id = ?, " +
                    "start_time = ?, end_time = ?, updated_at = CURRENT_TIMESTAMP " +
                    "WHERE id = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, normAppId);
                ps.setString(2, studentId);
                ps.setString(3, normIntId);
                ps.setString(4, normRoomId);
                ps.setTimestamp(5, Timestamp.valueOf(session.getStartTime()));
                ps.setTimestamp(6, Timestamp.valueOf(session.getEndTime()));
                ps.setString(7, normId);

                int affected = ps.executeUpdate();
                if (affected == 0) {
                    throw new EntityNotFoundException("InterviewSession with ID '" + normId + "' was not found.");
                }
            }
            return session;
        } catch (SQLException ex) {
            throw translateException("Update", ex);
        }
    }

    @Override
    public InterviewSession save(InterviewSession session) {
        validateEntityForWrite(session, "save");
        String normId = normalizeId(session.getId());
        String normAppId = normalizeId(session.getApplicationId());
        String normIntId = normalizeId(session.getInterviewerId());
        String normRoomId = normalizeId(session.getRoomId());

        try (Connection conn = getConnection()) {
            String studentId = resolveStudentId(conn, normAppId);

            String sql = "INSERT INTO interview_sessions (id, application_id, student_id, interviewer_id, room_id, " +
                    "start_time, end_time, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                    "ON CONFLICT (id) DO UPDATE SET " +
                    "application_id = EXCLUDED.application_id, " +
                    "student_id = EXCLUDED.student_id, " +
                    "interviewer_id = EXCLUDED.interviewer_id, " +
                    "room_id = EXCLUDED.room_id, " +
                    "start_time = EXCLUDED.start_time, " +
                    "end_time = EXCLUDED.end_time, " +
                    "updated_at = CURRENT_TIMESTAMP";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, normId);
                ps.setString(2, normAppId);
                ps.setString(3, studentId);
                ps.setString(4, normIntId);
                ps.setString(5, normRoomId);
                ps.setTimestamp(6, Timestamp.valueOf(session.getStartTime()));
                ps.setTimestamp(7, Timestamp.valueOf(session.getEndTime()));
                ps.executeUpdate();
            }
            return session;
        } catch (SQLException ex) {
            throw translateException("Save", ex);
        }
    }

    @Override
    public Optional<InterviewSession> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        String sql = "SELECT id, application_id, interviewer_id, room_id, start_time, end_time " +
                "FROM interview_sessions WHERE id = ?";

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
    public List<InterviewSession> findAll() {
        String sql = "SELECT id, application_id, interviewer_id, room_id, start_time, end_time " +
                "FROM interview_sessions ORDER BY start_time ASC, id ASC";
        List<InterviewSession> list = new ArrayList<>();

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
        String sql = "DELETE FROM interview_sessions WHERE id = ?";

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
        String sql = "SELECT 1 FROM interview_sessions WHERE id = ?";

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
        String sql = "SELECT COUNT(*) FROM interview_sessions";

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
        String sql = "DELETE FROM interview_sessions";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw translateException("Clear", ex);
        }
    }

    @Override
    public List<InterviewSession> findByApplicationId(String applicationId) {
        if (applicationId == null || applicationId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String sql = "SELECT id, application_id, interviewer_id, room_id, start_time, end_time " +
                "FROM interview_sessions WHERE application_id = ? ORDER BY start_time ASC";
        List<InterviewSession> list = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizeId(applicationId));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return Collections.unmodifiableList(list);
            }
        } catch (SQLException ex) {
            throw translateException("Find by application ID", ex);
        }
    }

    @Override
    public List<InterviewSession> findByInterviewerId(String interviewerId) {
        if (interviewerId == null || interviewerId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String sql = "SELECT id, application_id, interviewer_id, room_id, start_time, end_time " +
                "FROM interview_sessions WHERE interviewer_id = ? ORDER BY start_time ASC";
        List<InterviewSession> list = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizeId(interviewerId));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return Collections.unmodifiableList(list);
            }
        } catch (SQLException ex) {
            throw translateException("Find by interviewer ID", ex);
        }
    }

    @Override
    public List<InterviewSession> findByRoomId(String roomId) {
        if (roomId == null || roomId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String sql = "SELECT id, application_id, interviewer_id, room_id, start_time, end_time " +
                "FROM interview_sessions WHERE room_id = ? ORDER BY start_time ASC";
        List<InterviewSession> list = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizeId(roomId));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return Collections.unmodifiableList(list);
            }
        } catch (SQLException ex) {
            throw translateException("Find by room ID", ex);
        }
    }

    @Override
    public List<InterviewSession> findByStudentId(String studentId) {
        if (studentId == null || studentId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String sql = "SELECT id, application_id, interviewer_id, room_id, start_time, end_time " +
                "FROM interview_sessions WHERE student_id = ? ORDER BY start_time ASC";
        List<InterviewSession> list = new ArrayList<>();

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

    private String resolveStudentId(Connection conn, String applicationId) throws SQLException {
        String sql = "SELECT student_id FROM applications WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("student_id");
                }
                throw new ValidationException("Application with ID '" + applicationId + "' does not exist.");
            }
        }
    }

    private InterviewSession mapRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String applicationId = rs.getString("application_id");
        String interviewerId = rs.getString("interviewer_id");
        String roomId = rs.getString("room_id");
        Timestamp startTs = rs.getTimestamp("start_time");
        Timestamp endTs = rs.getTimestamp("end_time");
        LocalDateTime startTime = startTs.toLocalDateTime();
        LocalDateTime endTime = endTs.toLocalDateTime();
        return new InterviewSession(id, applicationId, interviewerId, roomId, startTime, endTime);
    }
}
