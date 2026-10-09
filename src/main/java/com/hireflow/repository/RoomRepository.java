package com.hireflow.repository;

import com.hireflow.model.Room;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contracts for Room entity operations.
 */
public interface RoomRepository extends CrudRepository<Room, String> {

    Optional<Room> findByName(String name);

    List<Room> findByMinCapacity(int minCapacity);
}
