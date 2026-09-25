package com.ngockhanh.clinic.healthcheck.domain.entity;

import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.valueobject.Money;

public final class HealthExaminationBatchEmployeeService {
    private final UUID id;
    private final UUID batchServiceId;
    private final UUID serviceRequestId;
    private final Money unitPrice;
    private final boolean billable;

    private HealthExaminationBatchEmployeeService(UUID id, UUID batchServiceId, UUID serviceRequestId,
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

    public static HealthExaminationBatchEmployeeService create(UUID id, UUID batchServiceId, UUID serviceRequestId, Money unitPrice) {
        return new HealthExaminationBatchEmployeeService(id, batchServiceId, serviceRequestId, unitPrice, false);
    }

    public static HealthExaminationBatchEmployeeService restore(UUID id, UUID batchServiceId, UUID serviceRequestId,
                                                          Money unitPrice, boolean billable) {
        return new HealthExaminationBatchEmployeeService(id, batchServiceId, serviceRequestId, unitPrice, billable);
    }

    public HealthExaminationBatchEmployeeService markedBillable() {
        return billable ? this : new HealthExaminationBatchEmployeeService(id, batchServiceId, serviceRequestId, unitPrice, true);
    }

    public HealthExaminationBatchEmployeeService withUnitPrice(Money price) {
        if (price == null) throw new IllegalArgumentException("Missing price");
        return new HealthExaminationBatchEmployeeService(id, batchServiceId, serviceRequestId, price, billable);
    }

    public UUID id() { return id; }
    public UUID batchServiceId() { return batchServiceId; }
    public UUID serviceRequestId() { return serviceRequestId; }
    public Money unitPrice() { return unitPrice; }
    public boolean billable() { return billable; }
}