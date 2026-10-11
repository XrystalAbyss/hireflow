package com.hireflow.repository;

import com.hireflow.db.DataSourceProvider;
import com.hireflow.db.TransactionManager;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.model.Interviewer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL JDBC implementation of {@link InterviewerRepository}.
 * Enforces company foreign-key validation, case-insensitive identity, and clean resource handling.
 */
public class JdbcInterviewerRepository extends AbstractJdbcRepository<Interviewer, String> implements InterviewerRepository {

    public JdbcInterviewerRepository(DataSource dataSource) {
        super(dataSource, Interviewer::getId, "Interviewer");
    }

    public JdbcInterviewerRepository(DataSourceProvider provider) {
        super(provider, Interviewer::getId, "Interviewer");
    }

    public JdbcInterviewerRepository(TransactionManager transactionManager) {
        super(transactionManager, Interviewer::getId, "Interviewer");
    }

    @Override
    public Interviewer add(Interviewer interviewer) {
        validateEntityForWrite(interviewer, "add");
        String sql = "INSERT INTO interviewers (id, name, company_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, interviewer.getId());
            ps.setString(2, interviewer.getName());
            ps.setString(3, interviewer.getCompanyId());
            ps.executeUpdate();
            return interviewer;
        } catch (SQLException ex) {
            throw translateException("Insert", ex);
        }
    }

    @Override
    public Interviewer update(Interviewer interviewer) {
        validateEntityForWrite(interviewer, "update");
        String sql = "UPDATE interviewers SET name = ?, company_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, interviewer.getName());
            ps.setString(2, interviewer.getCompanyId());
            ps.setString(3, interviewer.getId());

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new EntityNotFoundException("Interviewer with ID '" + interviewer.getId() + "' was not found.");
            }
            return interviewer;
        } catch (SQLException ex) {
            throw translateException("Update", ex);
        }
    }

    @Override
    public Interviewer save(Interviewer interviewer) {
        validateEntityForWrite(interviewer, "save");
        String sql = "INSERT INTO interviewers (id, name, company_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                "ON CONFLICT (id) DO UPDATE SET " +
                "name = EXCLUDED.name, " +
                "company_id = EXCLUDED.company_id, " +
                "updated_at = CURRENT_TIMESTAMP";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, interviewer.getId());
            ps.setString(2, interviewer.getName());
            ps.setString(3, interviewer.getCompanyId());
            ps.executeUpdate();
            return interviewer;
        } catch (SQLException ex) {
            throw translateException("Save", ex);
        }
    }

    @Override
    public Optional<Interviewer> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        String sql = "SELECT id, name, company_id FROM interviewers WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizeId(id));
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
    public List<Interviewer> findAll() {
        String sql = "SELECT id, name, company_id FROM interviewers ORDER BY id ASC";
        List<Interviewer> list = new ArrayList<>();

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
        String sql = "DELETE FROM interviewers WHERE id = ?";

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
        String sql = "SELECT 1 FROM interviewers WHERE id = ?";

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
        String sql = "SELECT COUNT(*) FROM interviewers";

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
        String sql = "DELETE FROM interviewers";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw translateException("Clear", ex);
        }
    }

    @Override
    public List<Interviewer> findByCompanyId(String companyId) {
        if (companyId == null || companyId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String sql = "SELECT id, name, company_id FROM interviewers WHERE UPPER(company_id) = UPPER(?) ORDER BY id ASC";
        List<Interviewer> list = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, companyId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return Collections.unmodifiableList(list);
        } catch (SQLException ex) {
            throw translateException("Find by company ID", ex);
        }
    }

    private Interviewer mapRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String name = rs.getString("name");
        String companyId = rs.getString("company_id");
        return new Interviewer(id, name, companyId);
    }
}
