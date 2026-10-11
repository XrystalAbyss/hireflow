package com.hireflow.repository;

import com.hireflow.db.DataSourceProvider;
import com.hireflow.db.TransactionManager;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.model.RecruitmentDrive;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

/**
 * PostgreSQL JDBC implementation of {@link DriveRepository}.
 * Atomically coordinates the parent recruitment drive and 1-to-many eligible branch mappings
 * without N+1 queries or nested transaction violations.
 */
public class JdbcDriveRepository extends AbstractJdbcRepository<RecruitmentDrive, String> implements DriveRepository {

    private static final String BASE_SELECT_SQL =
            "SELECT rd.id, rd.company_id, rd.job_title, rd.minimum_cgpa, rd.application_deadline, " +
            "COALESCE(array_agg(deb.branch) FILTER (WHERE deb.branch IS NOT NULL), ARRAY[]::varchar[]) AS branches " +
            "FROM recruitment_drives rd " +
            "LEFT JOIN drive_eligible_branches deb ON rd.id = deb.drive_id ";

    public JdbcDriveRepository(DataSource dataSource) {
        super(dataSource, RecruitmentDrive::getId, "RecruitmentDrive");
    }

    public JdbcDriveRepository(DataSourceProvider provider) {
        super(provider, RecruitmentDrive::getId, "RecruitmentDrive");
    }

    public JdbcDriveRepository(TransactionManager transactionManager) {
        super(transactionManager, RecruitmentDrive::getId, "RecruitmentDrive");
    }

    @Override
    public RecruitmentDrive add(RecruitmentDrive drive) {
        validateEntityForWrite(drive, "add");
        return executeTransactional(conn -> insertDriveWithBranches(conn, drive));
    }

    @Override
    public RecruitmentDrive update(RecruitmentDrive drive) {
        validateEntityForWrite(drive, "update");
        return executeTransactional(conn -> updateDriveWithBranches(conn, drive));
    }

    @Override
    public RecruitmentDrive save(RecruitmentDrive drive) {
        validateEntityForWrite(drive, "save");
        return executeTransactional(conn -> {
            if (existsByIdInternal(conn, drive.getId())) {
                return updateDriveWithBranches(conn, drive);
            } else {
                return insertDriveWithBranches(conn, drive);
            }
        });
    }

    @Override
    public Optional<RecruitmentDrive> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        String sql = BASE_SELECT_SQL +
                "WHERE rd.id = ? " +
                "GROUP BY rd.id, rd.company_id, rd.job_title, rd.minimum_cgpa, rd.application_deadline";

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
    public List<RecruitmentDrive> findAll() {
        String sql = BASE_SELECT_SQL +
                "GROUP BY rd.id, rd.company_id, rd.job_title, rd.minimum_cgpa, rd.application_deadline " +
                "ORDER BY rd.id ASC";
        List<RecruitmentDrive> list = new ArrayList<>();

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
        String sql = "DELETE FROM recruitment_drives WHERE id = ?";

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
        try (Connection conn = getConnection()) {
            return existsByIdInternal(conn, id);
        } catch (SQLException ex) {
            throw translateException("Exists by ID", ex);
        }
    }

    @Override
    public int count() {
        String sql = "SELECT COUNT(*) FROM recruitment_drives";

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
        executeTransactionalWithoutResult(conn -> {
            try (Statement s1 = conn.createStatement();
                 Statement s2 = conn.createStatement()) {
                s1.executeUpdate("DELETE FROM drive_eligible_branches");
                s2.executeUpdate("DELETE FROM recruitment_drives");
            }
        });
    }

    @Override
    public List<RecruitmentDrive> findByCompanyId(String companyId) {
        if (companyId == null || companyId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String sql = BASE_SELECT_SQL +
                "WHERE UPPER(rd.company_id) = UPPER(?) " +
                "GROUP BY rd.id, rd.company_id, rd.job_title, rd.minimum_cgpa, rd.application_deadline " +
                "ORDER BY rd.id ASC";
        List<RecruitmentDrive> list = new ArrayList<>();

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

    @Override
    public List<RecruitmentDrive> findActiveDrives(LocalDate asOfDate) {
        LocalDate date = asOfDate != null ? asOfDate : LocalDate.now();
        String sql = BASE_SELECT_SQL +
                "WHERE rd.application_deadline >= ? " +
                "GROUP BY rd.id, rd.company_id, rd.job_title, rd.minimum_cgpa, rd.application_deadline " +
                "ORDER BY rd.application_deadline ASC, rd.id ASC";
        List<RecruitmentDrive> list = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return Collections.unmodifiableList(list);
        } catch (SQLException ex) {
            throw translateException("Find active drives", ex);
        }
    }

    private RecruitmentDrive insertDriveWithBranches(Connection conn, RecruitmentDrive drive) throws SQLException {
        String insertDriveSql = "INSERT INTO recruitment_drives (id, company_id, job_title, minimum_cgpa, " +
                "application_deadline, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

        try (PreparedStatement psDrive = conn.prepareStatement(insertDriveSql)) {
            psDrive.setString(1, drive.getId());
            psDrive.setString(2, drive.getCompanyId());
            psDrive.setString(3, drive.getJobTitle());
            psDrive.setBigDecimal(4, BigDecimal.valueOf(drive.getMinimumCgpa()));
            psDrive.setDate(5, Date.valueOf(drive.getApplicationDeadline()));
            psDrive.executeUpdate();
        }

        insertBranches(conn, drive.getId(), drive.getEligibleBranches());
        return drive;
    }

    private RecruitmentDrive updateDriveWithBranches(Connection conn, RecruitmentDrive drive) throws SQLException {
        String updateDriveSql = "UPDATE recruitment_drives SET company_id = ?, job_title = ?, minimum_cgpa = ?, " +
                "application_deadline = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        int affected;
        try (PreparedStatement psDrive = conn.prepareStatement(updateDriveSql)) {
            psDrive.setString(1, drive.getCompanyId());
            psDrive.setString(2, drive.getJobTitle());
            psDrive.setBigDecimal(3, BigDecimal.valueOf(drive.getMinimumCgpa()));
            psDrive.setDate(4, Date.valueOf(drive.getApplicationDeadline()));
            psDrive.setString(5, drive.getId());
            affected = psDrive.executeUpdate();
        }

        if (affected == 0) {
            throw new EntityNotFoundException("RecruitmentDrive with ID '" + drive.getId() + "' was not found.");
        }

        // Transactionally synchronize branches: delete existing, then insert updated set
        String deleteBranchesSql = "DELETE FROM drive_eligible_branches WHERE drive_id = ?";
        try (PreparedStatement psDel = conn.prepareStatement(deleteBranchesSql)) {
            psDel.setString(1, drive.getId());
            psDel.executeUpdate();
        }

        insertBranches(conn, drive.getId(), drive.getEligibleBranches());
        return drive;
    }

    private void insertBranches(Connection conn, String driveId, Set<String> branches) throws SQLException {
        if (branches == null || branches.isEmpty()) {
            return;
        }

        String insertBranchSql = "INSERT INTO drive_eligible_branches (drive_id, branch) VALUES (?, ?)";
        try (PreparedStatement psBranch = conn.prepareStatement(insertBranchSql)) {
            for (String branch : branches) {
                if (branch != null && !branch.trim().isEmpty()) {
                    psBranch.setString(1, driveId);
                    psBranch.setString(2, branch.trim().toUpperCase());
                    psBranch.addBatch();
                }
            }
            psBranch.executeBatch();
        }
    }

    private boolean existsByIdInternal(Connection conn, String id) throws SQLException {
        String sql = "SELECT 1 FROM recruitment_drives WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizeId(id));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private RecruitmentDrive mapRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String companyId = rs.getString("company_id");
        String jobTitle = rs.getString("job_title");
        BigDecimal rawCgpa = rs.getBigDecimal("minimum_cgpa");
        double minCgpa = rawCgpa != null ? rawCgpa.doubleValue() : 0.0;
        Date sqlDate = rs.getDate("application_deadline");
        LocalDate deadline = sqlDate != null ? sqlDate.toLocalDate() : LocalDate.now();

        Set<String> branches = new HashSet<>();
        Array branchArray = rs.getArray("branches");
        if (branchArray != null) {
            Object arrayObj = branchArray.getArray();
            if (arrayObj instanceof String[] strArr) {
                for (String b : strArr) {
                    if (b != null && !b.trim().isEmpty()) {
                        branches.add(b.trim().toUpperCase());
                    }
                }
            }
        }

        return new RecruitmentDrive(id, companyId, jobTitle, minCgpa, branches, deadline);
    }
}
