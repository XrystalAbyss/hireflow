package com.hireflow.model;

import com.hireflow.exception.ValidationException;

import java.util.Objects;

/**
 * Represents a physical or virtual interview room / venue.
 */
public class Room {
    private final String id;
    private String name;
    private int capacity;

    public Room(String id, String name, int capacity) {
        validateId(id);
        validateName(name);
        validateCapacity(capacity);

        this.id = id.trim().toUpperCase();
        this.name = name.trim();
        this.capacity = capacity;
    }

    public static void validateId(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("Room ID cannot be null or blank.");
        }
    }

    public static void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Room name cannot be null or blank.");
        }
    }

    public static void validateCapacity(int capacity) {
        if (capacity <= 0) {
            throw new ValidationException("Room capacity must be greater than 0. Provided: " + capacity);
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        validateName(name);
        this.name = name.trim();
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        validateCapacity(capacity);
        this.capacity = capacity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Room room)) return false;
        return Objects.equals(id, room.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Room{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", capacity=" + capacity +
                '}';
    }
}
