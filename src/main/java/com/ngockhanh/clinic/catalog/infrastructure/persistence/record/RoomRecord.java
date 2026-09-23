package com.ngockhanh.clinic.catalog.infrastructure.persistence.record;

import java.util.UUID;

public record RoomRecord(
        UUID id,
        UUID departmentId,
        String roomCode,
        String roomName,
        String floor,
        String locationNote,
        String roomType,
        Boolean isActive
) {
}
