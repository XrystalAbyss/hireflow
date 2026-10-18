package com.hireflow.api.dto.officer;

import com.hireflow.model.Room;

public record OfficerRoomDetailDto(
        String id,
        String name,
        int capacity
) {
    public static OfficerRoomDetailDto fromDomain(Room room) {
        return new OfficerRoomDetailDto(
                room.getId(),
                room.getName(),
                room.getCapacity()
        );
    }
}
