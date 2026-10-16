package com.ngockhanh.clinic.catalog.domain.aggregate;

import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root for a medical specialty.
 */
public class Specialty {
    private static final int MAX_NAME_LENGTH = 200;

    private final UUID id;
    private final CatalogCode code;
    private String name;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long rowVersion;

    private Specialty(
            UUID id,
            CatalogCode code,
            String name,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long rowVersion) {
        if (id == null) throw new IllegalArgumentException("Specialty id is required");
        if (code == null) throw new IllegalArgumentException("Specialty code is required");
        if (rowVersion < 0) throw new IllegalArgumentException("Row version must not be negative");
        this.id = id;
        this.code = code;
        this.name = requiredName(name);
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.rowVersion = rowVersion;
    }

    public static Specialty create(UUID id, CatalogCode code, String name) {
        return new Specialty(id, code, name, true, null, null, 0);
    }

    public static Specialty restore(
            UUID id,
            CatalogCode code,
            String name,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long rowVersion) {
        return new Specialty(id, code, name, active, createdAt, updatedAt, rowVersion);
    }

    public void updateDetails(String name) {
        this.name = requiredName(name);
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
        return this == other || (other instanceof Specialty s && id.equals(s.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    private static String requiredName(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Specialty name is required");
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH)
            throw new IllegalArgumentException(
                    "Specialty name must not exceed " + MAX_NAME_LENGTH + " characters");
        return trimmed;
    }
}
