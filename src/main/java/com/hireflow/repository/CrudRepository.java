package com.hireflow.repository;

import java.util.List;
import java.util.Optional;

/**
 * Generic CRUD repository interface defining persistence contracts.
 * Decouples domain logic from persistence mechanisms.
 *
 * @param <T>  Domain entity type
 * @param <ID> Unique identifier type
 */
public interface CrudRepository<T, ID> {

    /**
     * Adds a new entity. Rejects null input and duplicate IDs.
     *
     * @param entity the entity to add
     * @return the saved entity
     */
    T add(T entity);

    /**
     * Updates an existing entity. Throws EntityNotFoundException if ID does not exist.
     *
     * @param entity the entity to update
     * @return the updated entity
     */
    T update(T entity);

    /**
     * Adds if entity ID is not present, or updates if already present.
     *
     * @param entity the entity to save
     * @return the saved entity
     */
    T save(T entity);

    /**
     * Finds entity by unique ID.
     */
    Optional<T> findById(ID id);

    /**
     * Returns an unmodifiable list of all stored entities.
     */
    List<T> findAll();

    /**
     * Deletes entity by ID.
     *
     * @return true if deleted, false if not found
     */
    boolean deleteById(ID id);

    /**
     * Checks if entity with ID exists.
     */
    boolean existsById(ID id);

    /**
     * Returns total count of entities in repository.
     */
    int count();

    /**
     * Clears all entities from repository.
     */
    void clear();
}
