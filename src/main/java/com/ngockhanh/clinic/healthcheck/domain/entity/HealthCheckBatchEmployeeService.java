package com.ngockhanh.clinic.healthcheck.domain.entity;

import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.valueobject.Money;

public final class HealthCheckBatchEmployeeService {
    private final UUID id;
    private final UUID batchServiceId;
    private final UUID serviceRequestId;
    private final Money unitPrice;
    private final boolean billable;

    private HealthCheckBatchEmployeeService(UUID id, UUID batchServiceId, UUID serviceRequestId,
                                            Money unitPrice, boolean billable) {
        if (id == null || batchServiceId == null || serviceRequestId == null || unitPrice == null) {
            throw new IllegalArgumentException("Invalid assignment");
        }
        this.id = id;
        this.batchServiceId = batchServiceId;
        this.serviceRequestId = serviceRequestId;
        this.unitPrice = unitPrice;
        this.billable = billable;
    }

    public static HealthCheckBatchEmployeeService create(UUID id, UUID batchServiceId, UUID serviceRequestId, Money unitPrice) {
        return new HealthCheckBatchEmployeeService(id, batchServiceId, serviceRequestId, unitPrice, false);
    }

    public static HealthCheckBatchEmployeeService restore(UUID id, UUID batchServiceId, UUID serviceRequestId,
                                                          Money unitPrice, boolean billable) {
        return new HealthCheckBatchEmployeeService(id, batchServiceId, serviceRequestId, unitPrice, billable);
    }

    public HealthCheckBatchEmployeeService markedBillable() {
        return billable ? this : new HealthCheckBatchEmployeeService(id, batchServiceId, serviceRequestId, unitPrice, true);
    }

    public HealthCheckBatchEmployeeService withUnitPrice(Money price) {
        if (price == null) throw new IllegalArgumentException("Missing price");
        return new HealthCheckBatchEmployeeService(id, batchServiceId, serviceRequestId, price, billable);
    }

    public UUID id() { return id; }
    public UUID batchServiceId() { return batchServiceId; }
    public UUID serviceRequestId() { return serviceRequestId; }
    public Money unitPrice() { return unitPrice; }
    public boolean billable() { return billable; }
}