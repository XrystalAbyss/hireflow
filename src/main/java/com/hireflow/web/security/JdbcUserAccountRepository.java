package com.hireflow.web.security;

import com.hireflow.db.DataSourceProvider;
import com.hireflow.db.TransactionManager;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.repository.AbstractJdbcRepository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL JDBC implementation of {@link UserAccountRepository}.
 * Manages persistent user credentials, role constraints, and student associations.
 */
public class JdbcUserAccountRepository extends AbstractJdbcRepository<UserAccount, String> implements UserAccountRepository {

    public JdbcUserAccountRepository(DataSource dataSource) {
        super(dataSource, UserAccount::getId, "UserAccount");
    }

    public JdbcUserAccountRepository(DataSourceProvider provider) {
        super(provider, UserAccount::getId, "UserAccount");
    }

    public JdbcUserAccountRepository(TransactionManager transactionManager) {
        super(transactionManager, UserAccount::getId, "UserAccount");
    }

    @Override
    public UserAccount add(UserAccount account) {
        validateEntityForWrite(account, "add");
        String sql = "INSERT INTO user_accounts (id, username, password_hash, role, student_id, display_name, " +
                "enabled, failed_login_attempts, locked_until, created_at, updated_at, last_login_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizeId(account.getId()));
            ps.setString(2, account.getUsername());
            ps.setString(3, account.getPasswordHash());
            ps.setString(4, account.getRole().name());
            ps.setString(5, account.getStudentId());
            ps.setString(6, account.getDisplayName());
            ps.setBoolean(7, account.isEnabled());
            ps.setInt(8, account.getFailedLoginAttempts());
            if (account.getLockedUntil() != null) {
                ps.setTimestamp(9, Timestamp.from(account.getLockedUntil()));
            } else {
                ps.setNull(9, Types.TIMESTAMP);
            }
            ps.setTimestamp(10, Timestamp.from(account.getCreatedAt()));
            ps.setTimestamp(11, Timestamp.from(account.getUpdatedAt()));
            if (account.getLastLoginAt() != null) {
                ps.setTimestamp(12, Timestamp.from(account.getLastLoginAt()));
            } else {
                ps.setNull(12, Types.TIMESTAMP);
            }

            ps.executeUpdate();
            return account;
        } catch (SQLException ex) {
            throw translateException("Insert", ex);
        }
    }

    @Override
    public UserAccount update(UserAccount account) {
        validateEntityForWrite(account, "update");
        String sql = "UPDATE user_accounts SET username = ?, password_hash = ?, role = ?, student_id = ?, " +
                "display_name = ?, enabled = ?, failed_login_attempts = ?, locked_until = ?, updated_at = ?, last_login_at = ? " +
                "WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, account.getUsername());
            ps.setString(2, account.getPasswordHash());
            ps.setString(3, account.getRole().name());
            ps.setString(4, account.getStudentId());
            ps.setString(5, account.getDisplayName());
            ps.setBoolean(6, account.isEnabled());
            ps.setInt(7, account.getFailedLoginAttempts());
            if (account.getLockedUntil() != null) {
                ps.setTimestamp(8, Timestamp.from(account.getLockedUntil()));
            } else {
                ps.setNull(8, Types.TIMESTAMP);
            }
            ps.setTimestamp(9, Timestamp.from(Instant.now()));
            if (account.getLastLoginAt() != null) {
                ps.setTimestamp(10, Timestamp.from(account.getLastLoginAt()));
            } else {
                ps.setNull(10, Types.TIMESTAMP);
            }
            ps.setString(11, normalizeId(account.getId()));

            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new EntityNotFoundException("UserAccount not found for update: " + account.getId());
            }
            return account;
        } catch (SQLException ex) {
            throw translateException("Update", ex);
        }
    }

    @Override
    public Optional<UserAccount> findById(String id) {
        String normalizedId = normalizeId(id);
        String sql = "SELECT * FROM user_accounts WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizedId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw translateException("FindById", ex);
        }
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }
        String normalized = username.trim().toLowerCase();
        String sql = "SELECT * FROM user_accounts WHERE username = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalized);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw translateException("FindByUsername", ex);
        }
    }

    @Override
    public Optional<UserAccount> findByStudentId(String studentId) {
        if (studentId == null || studentId.trim().isEmpty()) {
            return Optional.empty();
        }
        String normalized = studentId.trim().toUpperCase();
        String sql = "SELECT * FROM user_accounts WHERE student_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalized);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException ex) {
            throw translateException("FindByStudentId", ex);
        }
    }

    @Override
    public UserAccount save(UserAccount userAccount) {
        if (userAccount == null) {
            throw new IllegalArgumentException("UserAccount cannot be null");
        }
        if (existsById(userAccount.getId())) {
            return update(userAccount);
        } else {
            return add(userAccount);
        }
    }

    @Override
    public void updateLastLogin(String userId, Instant loginTime) {
        String normalizedId = normalizeId(userId);
        String sql = "UPDATE user_accounts SET last_login_at = ?, failed_login_attempts = 0, locked_until = NULL, " +
                "updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(loginTime != null ? loginTime : Instant.now()));
            ps.setString(2, normalizedId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw translateException("UpdateLastLogin", ex);
        }
    }

    @Override
    public void recordFailedAttempt(String userId, int attempts, Instant lockedUntil) {
        String normalizedId = normalizeId(userId);
        String sql = "UPDATE user_accounts SET failed_login_attempts = ?, locked_until = ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, attempts);
            if (lockedUntil != null) {
                ps.setTimestamp(2, Timestamp.from(lockedUntil));
            } else {
                ps.setNull(2, Types.TIMESTAMP);
            }
            ps.setString(3, normalizedId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw translateException("RecordFailedAttempt", ex);
        }
    }

    @Override
    public void resetFailedAttempts(String userId) {
        String normalizedId = normalizeId(userId);
        String sql = "UPDATE user_accounts SET failed_login_attempts = 0, locked_until = NULL, updated_at = CURRENT_TIMESTAMP " +
                "WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizedId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw translateException("ResetFailedAttempts", ex);
        }
    }

    @Override
    public boolean existsByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        String sql = "SELECT 1 FROM user_accounts WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw translateException("ExistsByUsername", ex);
        }
    }

    @Override
    public boolean existsById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        String sql = "SELECT 1 FROM user_accounts WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw translateException("ExistsById", ex);
        }
    }

    @Override
    public List<UserAccount> findAll() {
        String sql = "SELECT * FROM user_accounts ORDER BY username ASC";
        List<UserAccount> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapResultSet(rs));
            }
            return results;
        } catch (SQLException ex) {
            throw translateException("FindAll", ex);
        }
    }

    @Override
    public int count() {
        String sql = "SELECT COUNT(*) FROM user_accounts";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return (int) rs.getLong(1);
            }
            return 0;
        } catch (SQLException ex) {
            throw translateException("Count", ex);
        }
    }

    @Override
    public void clear() {
        deleteAll();
    }

    @Override
    public boolean deleteById(String id) {
        String normalizedId = normalizeId(id);
        String sql = "DELETE FROM user_accounts WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizedId);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw translateException("DeleteById", ex);
        }
    }

    @Override
    public void deleteAll() {
        String sql = "DELETE FROM user_accounts";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw translateException("DeleteAll", ex);
        }
    }

    private UserAccount mapResultSet(ResultSet rs) throws SQLException {
        Timestamp lockedUntilTs = rs.getTimestamp("locked_until");
        Timestamp lastLoginTs = rs.getTimestamp("last_login_at");

        return UserAccount.builder()
                .id(rs.getString("id"))
                .username(rs.getString("username"))
                .passwordHash(rs.getString("password_hash"))
                .role(UserRole.valueOf(rs.getString("role")))
                .studentId(rs.getString("student_id"))
                .displayName(rs.getString("display_name"))
                .enabled(rs.getBoolean("enabled"))
                .failedLoginAttempts(rs.getInt("failed_login_attempts"))
                .lockedUntil(lockedUntilTs != null ? lockedUntilTs.toInstant() : null)
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .lastLoginAt(lastLoginTs != null ? lastLoginTs.toInstant() : null)
                .build();
    }

    @Override
    protected String normalizeId(String id) {
        return id != null ? id.trim().toLowerCase() : null;
    }
}
