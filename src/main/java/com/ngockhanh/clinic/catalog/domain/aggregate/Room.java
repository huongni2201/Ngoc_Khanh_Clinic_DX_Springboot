package com.ngockhanh.clinic.catalog.domain.aggregate;

import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root for a clinic room. Room belongs to exactly one department (immutable).
 */
public class Room {
    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_TYPE_LENGTH = 50;

    private final UUID id;
    private final CatalogCode code;
    private String name;
    private final UUID departmentId; // immutable after creation per plan constraint
    private String roomType;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long rowVersion;

    private Room(
            UUID id,
            CatalogCode code,
            String name,
            UUID departmentId,
            String roomType,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long rowVersion) {
        if (id == null) throw new IllegalArgumentException("Room id is required");
        if (code == null) throw new IllegalArgumentException("Room code is required");
        if (departmentId == null) throw new IllegalArgumentException("Room departmentId is required");
        if (rowVersion < 0) throw new IllegalArgumentException("Row version must not be negative");
        this.id = id;
        this.code = code;
        this.name = requiredName(name);
        this.departmentId = departmentId;
        this.roomType = requiredType(roomType);
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.rowVersion = rowVersion;
    }

    /**
     * Creates a new active room with rowVersion=0. The department must be validated as active before
     * calling.
     */
    public static Room create(
            UUID id, CatalogCode code, String name, UUID departmentId, String roomType) {
        return new Room(id, code, name, departmentId, roomType, true, null, null, 0);
    }

    /**
     * Restores a room from its persistence record.
     */
    public static Room restore(
            UUID id,
            CatalogCode code,
            String name,
            UUID departmentId,
            String roomType,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long rowVersion) {
        return new Room(
                id, code, name, departmentId, roomType, active, createdAt, updatedAt, rowVersion);
    }

    /**
     * Replaces mutable room fields. departmentId is excluded (immutable per plan).
     */
    public void updateDetails(String name, String roomType) {
        this.name = requiredName(name);
        this.roomType = requiredType(roomType);
    }

    public void deactivate() {
        this.active = false;
    }

    public void reactivate() {
        this.active = true;
    }

    public UUID id() {
        return id;
    }

    public CatalogCode code() {
        return code;
    }

    public String name() {
        return name;
    }

    public UUID departmentId() {
        return departmentId;
    }

    public String roomType() {
        return roomType;
    }

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long rowVersion() {
        return rowVersion;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Room r && id.equals(r.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    private static String requiredName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Room name is required");
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH)
            throw new IllegalArgumentException(
                    "Room name must not exceed " + MAX_NAME_LENGTH + " characters");
        return trimmed;
    }

    private static String requiredType(String type) {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("Room type is required");
        String trimmed = type.trim();
        if (trimmed.length() > MAX_TYPE_LENGTH)
            throw new IllegalArgumentException(
                    "Room type must not exceed " + MAX_TYPE_LENGTH + " characters");
        return trimmed;
    }
}
