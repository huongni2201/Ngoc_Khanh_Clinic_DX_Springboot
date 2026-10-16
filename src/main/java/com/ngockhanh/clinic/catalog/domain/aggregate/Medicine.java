package com.ngockhanh.clinic.catalog.domain.aggregate;

import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root for a medicine in the prescription catalog.
 */
public class Medicine {
    private static final int MAX_GENERIC_NAME = 300;
    private static final int MAX_BRAND_NAME = 300;
    private static final int MAX_STRENGTH = 100;
    private static final int MAX_DOSAGE_FORM = 100;
    private static final int MAX_UNIT = 50;

    private final UUID id;
    private final CatalogCode code;
    private String genericName;
    private String brandName; // optional
    private String strength;
    private String dosageForm;
    private String unit;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long rowVersion;

    private Medicine(
            UUID id,
            CatalogCode code,
            String genericName,
            String brandName,
            String strength,
            String dosageForm,
            String unit,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long rowVersion) {
        if (id == null) throw new IllegalArgumentException("Medicine id is required");
        if (code == null) throw new IllegalArgumentException("Medicine code is required");
        if (rowVersion < 0) throw new IllegalArgumentException("Row version must not be negative");
        this.id = id;
        this.code = code;
        this.genericName = requiredStr(genericName, "genericName", MAX_GENERIC_NAME);
        this.brandName = optionalStr(brandName, "brandName", MAX_BRAND_NAME);
        this.strength = requiredStr(strength, "strength", MAX_STRENGTH);
        this.dosageForm = requiredStr(dosageForm, "dosageForm", MAX_DOSAGE_FORM);
        this.unit = requiredStr(unit, "unit", MAX_UNIT);
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.rowVersion = rowVersion;
    }

    public static Medicine create(
            UUID id,
            CatalogCode code,
            String genericName,
            String brandName,
            String strength,
            String dosageForm,
            String unit) {
        return new Medicine(
                id, code, genericName, brandName, strength, dosageForm, unit, true, null, null, 0);
    }

    public static Medicine restore(
            UUID id,
            CatalogCode code,
            String genericName,
            String brandName,
            String strength,
            String dosageForm,
            String unit,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long rowVersion) {
        return new Medicine(
                id,
                code,
                genericName,
                brandName,
                strength,
                dosageForm,
                unit,
                active,
                createdAt,
                updatedAt,
                rowVersion);
    }

    public void updateDetails(
            String genericName, String brandName, String strength, String dosageForm, String unit) {
        this.genericName = requiredStr(genericName, "genericName", MAX_GENERIC_NAME);
        this.brandName = optionalStr(brandName, "brandName", MAX_BRAND_NAME);
        this.strength = requiredStr(strength, "strength", MAX_STRENGTH);
        this.dosageForm = requiredStr(dosageForm, "dosageForm", MAX_DOSAGE_FORM);
        this.unit = requiredStr(unit, "unit", MAX_UNIT);
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

    public String genericName() {
        return genericName;
    }

    public String brandName() {
        return brandName;
    }

    public String strength() {
        return strength;
    }

    public String dosageForm() {
        return dosageForm;
    }

    public String unit() {
        return unit;
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
        return this == other || (other instanceof Medicine m && id.equals(m.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    private static String requiredStr(String value, String field, int max) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Medicine " + field + " is required");
        String trimmed = value.trim();
        if (trimmed.length() > max)
            throw new IllegalArgumentException(
                    "Medicine " + field + " must not exceed " + max + " characters");
        return trimmed;
    }

    private static String optionalStr(String value, String field, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > max)
            throw new IllegalArgumentException(
                    "Medicine " + field + " must not exceed " + max + " characters");
        return trimmed;
    }
}
