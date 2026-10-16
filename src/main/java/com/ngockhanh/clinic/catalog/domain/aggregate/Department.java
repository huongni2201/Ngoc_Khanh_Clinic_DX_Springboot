package com.ngockhanh.clinic.catalog.domain.aggregate;

import com.ngockhanh.clinic.catalog.domain.enums.DepartmentType;
import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root for a clinic department.
 *
 * <p>The code is set once at creation and is immutable. Active status is changed via dedicated use
 * cases; the row version is used for optimistic concurrency.
 */
public class Department {
    private static final int MAX_NAME_LENGTH = 200;

    private final UUID id;
    private final CatalogCode code;
    private String name;
    private DepartmentType departmentType;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long rowVersion;

    private Department(
            UUID id,
            CatalogCode code,
            String name,
            DepartmentType departmentType,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long rowVersion) {
        if (id == null) throw new IllegalArgumentException("Department id is required");
        if (code == null) throw new IllegalArgumentException("Department code is required");
        if (departmentType == null) throw new IllegalArgumentException("Department type is required");
        if (rowVersion < 0) throw new IllegalArgumentException("Row version must not be negative");
        this.id = id;
        this.code = code;
        this.name = requiredName(name);
        this.departmentType = departmentType;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.rowVersion = rowVersion;
    }

    /**
     * Creates a new active department with rowVersion=0.
     */
    public static Department create(
            UUID id, CatalogCode code, String name, DepartmentType departmentType) {
        return new Department(id, code, name, departmentType, true, null, null, 0);
    }

    /**
     * Restores a department from its persistence record.
     */
    public static Department restore(
            UUID id,
            CatalogCode code,
            String name,
            DepartmentType departmentType,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long rowVersion) {
        return new Department(id, code, name, departmentType, active, createdAt, updatedAt, rowVersion);
    }

    /**
     * Replaces mutable department fields. The code, id, active flag and rowVersion are not changed
     * here.
     */
    public void updateDetails(String name, DepartmentType departmentType) {
        if (departmentType == null) throw new IllegalArgumentException("Department type is required");
        this.name = requiredName(name);
        this.departmentType = departmentType;
    }

    /**
     * Sets active=false. No invariant check: call after the dependency guard in the use case.
     */
    public void deactivate() {
        this.active = false;
    }

    /**
     * Sets active=true. No parent check: call after parent-active validation in the use case.
     */
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

    public DepartmentType departmentType() {
        return departmentType;
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
        return this == other || (other instanceof Department d && id.equals(d.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    private static String requiredName(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Department name is required");
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH)
            throw new IllegalArgumentException(
                    "Department name must not exceed " + MAX_NAME_LENGTH + " characters");
        return trimmed;
    }
}
