package com.ngockhanh.clinic.healthexamination.domain.entity;

import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;

public record HealthExaminationBatchService(
    AggregateId id,
    AggregateId serviceId,
    AggregateId batchId,
    String serviceCode,
    String serviceName,
    Money negotiatedPrice,
    AggregateId templateVersionId,
    int displayOrder,
    String status) {
  public HealthExaminationBatchService {
    if (id == null
        || serviceId == null
        || batchId == null
        || serviceCode == null
        || serviceCode.isBlank()
        || serviceName == null
        || serviceName.isBlank()
        || negotiatedPrice == null
        || displayOrder < 1
        || status == null
        || status.isBlank()) throw new IllegalArgumentException("Invalid batch service");
  }

  public static HealthExaminationBatchService create(
      AggregateId id,
      AggregateId serviceId,
      AggregateId batchId,
      String code,
      Money price,
      AggregateId template) {
    return create(id, serviceId, batchId, code, code, price, template, 1, "ACTIVE");
  }

  public static HealthExaminationBatchService create(
      AggregateId id,
      AggregateId serviceId,
      AggregateId batchId,
      String code,
      String name,
      Money price,
      AggregateId template,
      int order,
      String status) {
    return new HealthExaminationBatchService(
        id, serviceId, batchId, code, name, price, template, order, status);
  }

  public HealthExaminationBatchService withNegotiatedPrice(Money price) {
    return new HealthExaminationBatchService(
        id,
        serviceId,
        batchId,
        serviceCode,
        serviceName,
        price,
        templateVersionId,
        displayOrder,
        status);
  }
}
