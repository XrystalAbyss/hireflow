package com.hireflow.repository;

import com.hireflow.model.Room;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * In-memory thread-safe implementation of RoomRepository.
 */
public class InMemoryRoomRepository extends AbstractInMemoryRepository<Room, String> implements RoomRepository {

    public InMemoryRoomRepository() {
        super(Room::getId, "Room");
    }

    @Override
    public Optional<Room> findByName(String name) {
        if (name == null) return Optional.empty();
        String normalized = name.trim();
        return store.values().stream()
                .filter(r -> r.getName().equalsIgnoreCase(normalized))
                .findFirst();
    }

    @Override
    public List<Room> findByMinCapacity(int minCapacity) {
        List<Room> list = store.values().stream()
                .filter(r -> r.getCapacity() >= minCapacity)
                .toList();
        return Collections.unmodifiableList(list);
    }
}
