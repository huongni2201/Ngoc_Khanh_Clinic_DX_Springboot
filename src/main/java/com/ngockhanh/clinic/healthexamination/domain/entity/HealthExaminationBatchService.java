package com.ngockhanh.clinic.healthexamination.domain.entity;

import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;

public final class HealthExaminationBatchService {
    private final AggregateId id;
    private final AggregateId serviceId;
    private final AggregateId batchId;
    private final String serviceCode;
    private final Money basePrice;
    private final Money negotiatedPrice;
    private final AggregateId templateVersionId;

    private HealthExaminationBatchService(AggregateId id, AggregateId serviceId, AggregateId batchId,
                                          String serviceCode, Money basePrice, Money negotiatedPrice,
                                          AggregateId templateVersionId) {
        if (id == null || serviceId == null || batchId == null || serviceCode == null || serviceCode.isBlank()
                || basePrice == null || negotiatedPrice == null) {
            throw new IllegalArgumentException("Invalid batch service");
        }
        this.id = id;
        this.serviceId = serviceId;
        this.batchId = batchId;
        this.serviceCode = serviceCode;
        this.basePrice = basePrice;
        this.negotiatedPrice = negotiatedPrice;
        this.templateVersionId = templateVersionId;
    }

    public static HealthExaminationBatchService create(AggregateId id, AggregateId serviceId, AggregateId batchId,
                                                       String serviceCode, Money basePrice, Money negotiatedPrice,
                                                       AggregateId templateVersionId) {
        return new HealthExaminationBatchService(id, serviceId, batchId, serviceCode, basePrice,
                negotiatedPrice, templateVersionId);
    }

    public static HealthExaminationBatchService restore(AggregateId id, AggregateId serviceId, AggregateId batchId,
                                                        String serviceCode, Money basePrice, Money negotiatedPrice,
                                                        AggregateId templateVersionId) {
        return new HealthExaminationBatchService(id, serviceId, batchId, serviceCode, basePrice,
                negotiatedPrice, templateVersionId);
    }

    public HealthExaminationBatchService withNegotiatedPrice(Money price) {
        if (price == null) throw new IllegalArgumentException("Missing price");
        return new HealthExaminationBatchService(id, serviceId, batchId, serviceCode, basePrice,
                price, templateVersionId);
    }

    public AggregateId id() { return id; }
    public AggregateId serviceId() { return serviceId; }
    public AggregateId batchId() { return batchId; }
    public String serviceCode() { return serviceCode; }
    public Money basePrice() { return basePrice; }
    public Money negotiatedPrice() { return negotiatedPrice; }
    public AggregateId templateVersionId() { return templateVersionId; }
}
