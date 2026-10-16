package com.ngockhanh.clinic.catalog.domain.aggregate;

import com.ngockhanh.clinic.catalog.domain.enums.ServiceType;
import com.ngockhanh.clinic.catalog.domain.valueobject.CatalogCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root for a clinic service.
 *
 * <p>performingDepartmentId is immutable after creation per plan constraint. specialtyId is
 * optional and may be updated. unitPrice must be non-negative and have at most two decimal places.
 */
public class CatalogService {
    private static final int MAX_NAME_LENGTH = 300;
    private static final int MAX_INSTRUCTION_LENGTH = 4000;

    private final UUID id;
    private final CatalogCode code;
    private String name;
    private ServiceType serviceType;
    private final UUID performingDepartmentId; // immutable
    private UUID specialtyId; // optional, mutable
    private BigDecimal unitPrice;
    private String preparationInstruction; // optional, mutable
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long rowVersion;

    private CatalogService(
            UUID id,
            CatalogCode code,
            String name,
            ServiceType serviceType,
            UUID performingDepartmentId,
            UUID specialtyId,
            BigDecimal unitPrice,
            String preparationInstruction,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long rowVersion) {
        if (id == null) throw new IllegalArgumentException("Service id is required");
        if (code == null) throw new IllegalArgumentException("Service code is required");
        if (serviceType == null) throw new IllegalArgumentException("Service type is required");
        if (performingDepartmentId == null)
            throw new IllegalArgumentException("Service performingDepartmentId is required");
        if (rowVersion < 0) throw new IllegalArgumentException("Row version must not be negative");
        this.id = id;
        this.code = code;
        this.name = requiredName(name);
        this.serviceType = serviceType;
        this.performingDepartmentId = performingDepartmentId;
        this.specialtyId = specialtyId;
        this.unitPrice = validatedPrice(unitPrice);
        this.preparationInstruction = optionalInstruction(preparationInstruction);
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.rowVersion = rowVersion;
    }

    public static CatalogService create(
            UUID id,
            CatalogCode code,
            String name,
            ServiceType serviceType,
            UUID performingDepartmentId,
            UUID specialtyId,
            BigDecimal unitPrice,
            String preparationInstruction) {
        return new CatalogService(
                id,
                code,
                name,
                serviceType,
                performingDepartmentId,
                specialtyId,
                unitPrice,
                preparationInstruction,
                true,
                null,
                null,
                0);
    }

    public static CatalogService restore(
            UUID id,
            CatalogCode code,
            String name,
            ServiceType serviceType,
            UUID performingDepartmentId,
            UUID specialtyId,
            BigDecimal unitPrice,
            String preparationInstruction,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long rowVersion) {
        return new CatalogService(
                id,
                code,
                name,
                serviceType,
                performingDepartmentId,
                specialtyId,
                unitPrice,
                preparationInstruction,
                active,
                createdAt,
                updatedAt,
                rowVersion);
    }

    /**
     * Updates mutable service fields. performingDepartmentId is excluded (immutable). specialtyId may
     * be set to null to remove the optional link.
     */
    public void updateDetails(
            String name,
            ServiceType serviceType,
            UUID specialtyId,
            BigDecimal unitPrice,
            String preparationInstruction) {
        if (serviceType == null) throw new IllegalArgumentException("Service type is required");
        this.name = requiredName(name);
        this.serviceType = serviceType;
        this.specialtyId = specialtyId;
        this.unitPrice = validatedPrice(unitPrice);
        this.preparationInstruction = optionalInstruction(preparationInstruction);
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

    public ServiceType serviceType() {
        return serviceType;
    }

    public UUID performingDepartmentId() {
        return performingDepartmentId;
    }

    public UUID specialtyId() {
        return specialtyId;
    }

    public BigDecimal unitPrice() {
        return unitPrice;
    }

    public String preparationInstruction() {
        return preparationInstruction;
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
        return this == other || (other instanceof CatalogService s && id.equals(s.id));
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    private static String requiredName(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Service name is required");
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH)
            throw new IllegalArgumentException(
                    "Service name must not exceed " + MAX_NAME_LENGTH + " characters");
        return trimmed;
    }

    private static BigDecimal validatedPrice(BigDecimal price) {
        if (price == null) throw new IllegalArgumentException("Service unit price is required");
        if (price.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Service unit price must not be negative");
        if (price.scale() > 2)
            throw new IllegalArgumentException("Service unit price must have at most 2 decimal places");
        if ((long) price.precision() - price.scale() > 12)
            throw new IllegalArgumentException("Service unit price must have at most 12 integer digits");
        // Validate scale precisely without silent rounding
        try {
            return price.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Service unit price must have at most 2 decimal places");
        }
    }

    private static String optionalInstruction(String instruction) {
        if (instruction == null || instruction.isBlank()) return null;
        String trimmed = instruction.trim();
        if (trimmed.length() > MAX_INSTRUCTION_LENGTH)
            throw new IllegalArgumentException(
                    "Preparation instruction must not exceed " + MAX_INSTRUCTION_LENGTH + " characters");
        return trimmed;
    }
}
