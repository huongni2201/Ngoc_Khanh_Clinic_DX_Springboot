package com.ngockhanh.clinic.healthcheck.domain.entity;

import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.valueobject.Money;

public final class HealthCheckBatchService {
    private final UUID id;
    private final UUID serviceId;
    private final UUID batchId;
    private final String serviceCode;
    private final Money basePrice;
    private final Money negotiatedPrice;
    private final UUID templateVersionId;

    private HealthCheckBatchService(UUID id, UUID serviceId, UUID batchId, String serviceCode,
                                    Money basePrice, Money negotiatedPrice, UUID templateVersionId) {
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

    public static HealthCheckBatchService create(UUID id, UUID serviceId, UUID batchId, String serviceCode,
                                                 Money basePrice, Money negotiatedPrice, UUID templateVersionId) {
        return new HealthCheckBatchService(id, serviceId, batchId, serviceCode, basePrice, negotiatedPrice, templateVersionId);
    }

    public static HealthCheckBatchService restore(UUID id, UUID serviceId, UUID batchId, String serviceCode,
                                                  Money basePrice, Money negotiatedPrice, UUID templateVersionId) {
        return new HealthCheckBatchService(id, serviceId, batchId, serviceCode, basePrice, negotiatedPrice, templateVersionId);
    }

    public HealthCheckBatchService withNegotiatedPrice(Money price) {
        if (price == null) throw new IllegalArgumentException("Missing price");
        return new HealthCheckBatchService(id, serviceId, batchId, serviceCode, basePrice, price, templateVersionId);
    }

    public UUID id() { return id; }
    public UUID serviceId() { return serviceId; }
    public UUID batchId() { return batchId; }
    public String serviceCode() { return serviceCode; }
    public Money basePrice() { return basePrice; }
    public Money negotiatedPrice() { return negotiatedPrice; }
    public UUID templateVersionId() { return templateVersionId; }
}