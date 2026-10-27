package com.hireflow.web.security;

import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory implementation of {@link UserAccountRepository}.
 * Used during IN_MEMORY storage mode and lightweight unit tests.
 */
public class InMemoryUserAccountRepository implements UserAccountRepository {

    private final Map<String, UserAccount> byId = new ConcurrentHashMap<>();
    private final Map<String, String> idByUsername = new ConcurrentHashMap<>();

    @Override
    public UserAccount add(UserAccount entity) {
        if (entity == null) {
            throw new IllegalArgumentException("UserAccount cannot be null");
        }
        if (byId.containsKey(entity.getId())) {
            throw new DuplicateEntityException("UserAccount with ID already exists: " + entity.getId());
        }
        if (idByUsername.containsKey(entity.getUsername())) {
            throw new DuplicateEntityException("UserAccount with username already exists: " + entity.getUsername());
        }
        byId.put(entity.getId(), entity);
        idByUsername.put(entity.getUsername(), entity.getId());
        return entity;
    }

    @Override
    public UserAccount update(UserAccount entity) {
        if (entity == null) {
            throw new IllegalArgumentException("UserAccount cannot be null");
        }
        if (!byId.containsKey(entity.getId())) {
            throw new EntityNotFoundException("UserAccount not found for update: " + entity.getId());
        }
        byId.put(entity.getId(), entity);
        idByUsername.put(entity.getUsername(), entity.getId());
        return entity;
    }

    @Override
    public UserAccount save(UserAccount userAccount) {
        if (userAccount == null) {
            throw new IllegalArgumentException("UserAccount cannot be null");
        }
        if (byId.containsKey(userAccount.getId())) {
            return update(userAccount);
        } else {
            return add(userAccount);
        }
    }

    @Override
    public Optional<UserAccount> findById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        if (username == null) return Optional.empty();
        String normalized = username.trim().toLowerCase();
        String id = idByUsername.get(normalized);
        if (id == null) return Optional.empty();
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<UserAccount> findByStudentId(String studentId) {
        if (studentId == null) return Optional.empty();
        String target = studentId.trim().toUpperCase();
        return byId.values().stream()
                .filter(a -> target.equalsIgnoreCase(a.getStudentId()))
                .findFirst();
    }

    @Override
    public List<UserAccount> findAll() {
        return new ArrayList<>(byId.values());
    }

    @Override
    public boolean deleteById(String id) {
        if (id == null) return false;
        UserAccount removed = byId.remove(id);
        if (removed != null) {
            idByUsername.remove(removed.getUsername());
            return true;
        }
        return false;
    }

    @Override
    public boolean existsById(String id) {
        if (id == null) return false;
        return byId.containsKey(id);
    }

    @Override
    public int count() {
        return byId.size();
    }

    @Override
    public void clear() {
        deleteAll();
    }

    @Override
    public void updateLastLogin(String userId, Instant loginTime) {
        byId.computeIfPresent(userId, (id, current) ->
                current.toBuilder()
                        .lastLoginAt(loginTime)
                        .failedLoginAttempts(0)
                        .lockedUntil(null)
                        .updatedAt(Instant.now())
                        .build());
    }

    @Override
    public void recordFailedAttempt(String userId, int attempts, Instant lockedUntil) {
        byId.computeIfPresent(userId, (id, current) ->
                current.toBuilder()
                        .failedLoginAttempts(attempts)
                        .lockedUntil(lockedUntil)
                        .updatedAt(Instant.now())
                        .build());
    }

    @Override
    public void resetFailedAttempts(String userId) {
        byId.computeIfPresent(userId, (id, current) ->
                current.toBuilder()
                        .failedLoginAttempts(0)
                        .lockedUntil(null)
                        .updatedAt(Instant.now())
                        .build());
    }

    @Override
    public boolean existsByUsername(String username) {
        if (username == null) return false;
        return idByUsername.containsKey(username.trim().toLowerCase());
    }

    @Override
    public void deleteAll() {
        byId.clear();
        idByUsername.clear();
    }
}
