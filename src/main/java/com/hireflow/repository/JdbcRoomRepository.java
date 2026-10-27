package com.hireflow.repository;

import com.hireflow.db.DataSourceProvider;
import com.hireflow.db.TransactionManager;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.model.Room;

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
 * PostgreSQL JDBC implementation of {@link RoomRepository}.
 * Manages interview venues, capacity queries, and constraints.
 */
public class JdbcRoomRepository extends AbstractJdbcRepository<Room, String> implements RoomRepository {

    public JdbcRoomRepository(DataSource dataSource) {
        super(dataSource, Room::getId, "Room");
    }

    public JdbcRoomRepository(DataSourceProvider provider) {
        super(provider, Room::getId, "Room");
    }

    public JdbcRoomRepository(TransactionManager transactionManager) {
        super(transactionManager, Room::getId, "Room");
    }

    @Override
    public Room add(Room room) {
        validateEntityForWrite(room, "add");
        String sql = "INSERT INTO rooms (id, name, capacity, created_at, updated_at) " +
                "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, room.getId());
            ps.setString(2, room.getName());
            ps.setInt(3, room.getCapacity());
            ps.executeUpdate();
            return room;
        } catch (SQLException ex) {
            throw translateException("Insert", ex);
        }
    }

    @Override
    public Room update(Room room) {
        validateEntityForWrite(room, "update");
        String sql = "UPDATE rooms SET name = ?, capacity = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, room.getName());
            ps.setInt(2, room.getCapacity());
            ps.setString(3, room.getId());

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new EntityNotFoundException("Room with ID '" + room.getId() + "' was not found.");
            }
            return room;
        } catch (SQLException ex) {
            throw translateException("Update", ex);
        }
    }

    @Override
    public Room save(Room room) {
        validateEntityForWrite(room, "save");
        String sql = "INSERT INTO rooms (id, name, capacity, created_at, updated_at) " +
                "VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                "ON CONFLICT (id) DO UPDATE SET " +
                "name = EXCLUDED.name, " +
                "capacity = EXCLUDED.capacity, " +
                "updated_at = CURRENT_TIMESTAMP";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, room.getId());
            ps.setString(2, room.getName());
            ps.setInt(3, room.getCapacity());
            ps.executeUpdate();
            return room;
        } catch (SQLException ex) {
            throw translateException("Save", ex);
        }
    }

    @Override
    public Optional<Room> findById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        String sql = "SELECT id, name, capacity FROM rooms WHERE id = ?";

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
    public List<Room> findAll() {
        String sql = "SELECT id, name, capacity FROM rooms ORDER BY id ASC";
        List<Room> list = new ArrayList<>();

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
        String sql = "DELETE FROM rooms WHERE id = ?";

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
        String sql = "SELECT 1 FROM rooms WHERE id = ?";

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
        String sql = "SELECT COUNT(*) FROM rooms";

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
        String sql = "DELETE FROM rooms";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw translateException("Clear", ex);
        }
    }

    @Override
    public Optional<Room> findByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Optional.empty();
        }
        String sql = "SELECT id, name, capacity FROM rooms WHERE LOWER(name) = LOWER(?) LIMIT 1";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException ex) {
            throw translateException("Find by name", ex);
        }
    }

    @Override
    public List<Room> findByMinCapacity(int minCapacity) {
        String sql = "SELECT id, name, capacity FROM rooms WHERE capacity >= ? ORDER BY capacity ASC, id ASC";
        List<Room> list = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, minCapacity);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return Collections.unmodifiableList(list);
        } catch (SQLException ex) {
            throw translateException("Find by min capacity", ex);
        }
    }

    private Room mapRow(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String name = rs.getString("name");
        int capacity = rs.getInt("capacity");
        return new Room(id, name, capacity);
    }
}
