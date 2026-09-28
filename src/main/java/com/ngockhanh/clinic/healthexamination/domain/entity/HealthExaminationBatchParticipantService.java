package com.ngockhanh.clinic.healthexamination.domain.entity;

import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;

public final class HealthExaminationBatchParticipantService {
    private final AggregateId id;
    private final AggregateId batchServiceId;
    private final AggregateId serviceRequestId;
    private final Money unitPrice;
    private final boolean billable;

    private HealthExaminationBatchParticipantService(AggregateId id, AggregateId batchServiceId,
                                                    AggregateId serviceRequestId, Money unitPrice,
                                                    boolean billable) {
        if (id == null || batchServiceId == null || serviceRequestId == null || unitPrice == null) {
            throw new IllegalArgumentException("Invalid participant assignment");
        }
        this.id = id;
        this.batchServiceId = batchServiceId;
        this.serviceRequestId = serviceRequestId;
        this.unitPrice = unitPrice;
        this.billable = billable;
    }

    public static HealthExaminationBatchParticipantService create(AggregateId id, AggregateId batchServiceId,
                                                                   AggregateId serviceRequestId, Money unitPrice) {
        return new HealthExaminationBatchParticipantService(id, batchServiceId, serviceRequestId, unitPrice, false);
    }

    public static HealthExaminationBatchParticipantService restore(AggregateId id, AggregateId batchServiceId,
                                                                    AggregateId serviceRequestId, Money unitPrice,
                                                                    boolean billable) {
        return new HealthExaminationBatchParticipantService(id, batchServiceId, serviceRequestId, unitPrice, billable);
    }

    public HealthExaminationBatchParticipantService markedBillable() {
        return billable ? this : new HealthExaminationBatchParticipantService(
                id, batchServiceId, serviceRequestId, unitPrice, true);
    }

    public HealthExaminationBatchParticipantService withUnitPrice(Money price) {
        if (price == null) throw new IllegalArgumentException("Missing price");
        return new HealthExaminationBatchParticipantService(id, batchServiceId, serviceRequestId, price, billable);
    }

    public AggregateId id() { return id; }
    public AggregateId batchServiceId() { return batchServiceId; }
    public AggregateId serviceRequestId() { return serviceRequestId; }
    public Money unitPrice() { return unitPrice; }
    public boolean billable() { return billable; }
}
