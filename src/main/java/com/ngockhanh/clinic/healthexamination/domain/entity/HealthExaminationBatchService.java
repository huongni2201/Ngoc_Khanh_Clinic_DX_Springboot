package com.ngockhanh.clinic.healthexamination.domain.entity;

import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;

public record HealthExaminationBatchService(
    AggregateId id,
    AggregateId serviceId,
    AggregateId batchId,
    Money referencePriceSnapshot,
    Money negotiatedPrice,
    int displayOrder,
    boolean active,
    long rowVersion) {
  public HealthExaminationBatchService {
    if (id == null || serviceId == null || batchId == null || displayOrder < 1 || rowVersion < 0)
      throw new IllegalArgumentException("Invalid batch service");
    validatePrice(referencePriceSnapshot);
    validatePrice(negotiatedPrice);
  }

  private static void validatePrice(Money price) {
    if (price == null
        || !"VND".equals(price.currency())
        || (long) price.amount().precision() - price.amount().scale() > 12)
      throw new IllegalArgumentException("Invalid batch price");
  }

  public HealthExaminationBatchService withNegotiatedPrice(Money price) {
    return new HealthExaminationBatchService(
        id, serviceId, batchId, referencePriceSnapshot, price, displayOrder, active, rowVersion);
  }
}
