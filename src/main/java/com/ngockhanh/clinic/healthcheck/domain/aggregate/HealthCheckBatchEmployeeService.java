package com.ngockhanh.clinic.healthcheck.domain.aggregate;

import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.exception.DomainRuleViolation;

import com.ngockhanh.clinic.healthcheck.domain.valueobject.Money;

public final class HealthCheckBatchEmployeeService {
    private final UUID batchServiceId;
    private final UUID serviceRequestId;
    private Money unitPrice;
    private Boolean billable;

    HealthCheckBatchEmployeeService(UUID batchServiceId, UUID serviceRequestId, Money unitPrice) {
        if (batchServiceId == null || serviceRequestId == null || unitPrice == null) throw new IllegalArgumentException("Invalid assignment");
        this.batchServiceId = batchServiceId;
        this.serviceRequestId = serviceRequestId;
        this.unitPrice = unitPrice;
    }

    public static HealthCheckBatchEmployeeService restore(UUID batchServiceId, UUID serviceRequestId, Money unitPrice,
                                                          Boolean billable) {
        HealthCheckBatchEmployeeService assignment = new HealthCheckBatchEmployeeService(batchServiceId, serviceRequestId, unitPrice);
        assignment.billable = billable;
        return assignment;
    }

    public UUID batchServiceId() { return batchServiceId; }

    public Boolean billable() { return billable; }
    public Money unitPrice() { return unitPrice; }
    public UUID serviceRequestId() { return serviceRequestId; }

    void markBillable(Boolean completedSuccessfully) {
        if (!completedSuccessfully) throw new DomainRuleViolation("Service Request not completed");
        billable = true;
    }

    void reprice(Money price) { unitPrice = price; }
}

