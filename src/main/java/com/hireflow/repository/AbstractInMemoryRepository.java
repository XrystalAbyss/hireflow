package com.hireflow.repository;

import com.hireflow.exception.DuplicateEntityException;
import com.hireflow.exception.EntityNotFoundException;
import com.hireflow.exception.ValidationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Base generic in-memory repository backed by ConcurrentHashMap.
 * Enforces defensive checks, duplicate ID prevention, and immutable collection views.
 *
 * @param <T>  Entity type
 * @param <ID> Entity identifier type
 */
public abstract class AbstractInMemoryRepository<T, ID> implements CrudRepository<T, ID> {
    protected final Map<ID, T> store = new ConcurrentHashMap<>();
    private final Function<T, ID> idExtractor;
    private final String entityName;

    protected AbstractInMemoryRepository(Function<T, ID> idExtractor, String entityName) {
        this.idExtractor = Objects.requireNonNull(idExtractor, "ID extractor must not be null.");
        this.entityName = Objects.requireNonNull(entityName, "Entity name must not be null.");
    }

    @Override
    public T add(T entity) {
        if (entity == null) {
            throw new ValidationException(entityName + " to add cannot be null.");
        }
        ID id = idExtractor.apply(entity);
        if (id == null) {
            throw new ValidationException(entityName + " ID cannot be null.");
        }
        if (store.containsKey(id)) {
            throw new DuplicateEntityException(entityName + " with ID '" + id + "' already exists.");
        }
        store.put(id, entity);
        return entity;
    }

    @Override
    public T update(T entity) {
        if (entity == null) {
            throw new ValidationException(entityName + " to update cannot be null.");
        }
        ID id = idExtractor.apply(entity);
        if (id == null) {
            throw new ValidationException(entityName + " ID cannot be null.");
        }
        if (!store.containsKey(id)) {
            throw new EntityNotFoundException(entityName + " with ID '" + id + "' was not found.");
        }
        store.put(id, entity);
        return entity;
    }

    @Override
    public T save(T entity) {
        if (entity == null) {
            throw new ValidationException(entityName + " to save cannot be null.");
        }
        ID id = idExtractor.apply(entity);
        if (id == null) {
            throw new ValidationException(entityName + " ID cannot be null.");
        }
        store.put(id, entity);
        return entity;
    }

    @Override
    public Optional<T> findById(ID id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<T> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(store.values()));
    }

    @Override
    public boolean deleteById(ID id) {
        if (id == null) return false;
        return store.remove(id) != null;
    }

    @Override
    public boolean existsById(ID id) {
        if (id == null) return false;
        return store.containsKey(id);
    }

    @Override
    public int count() {
        return store.size();
    }

    @Override
    public void clear() {
        store.clear();
    }
}
