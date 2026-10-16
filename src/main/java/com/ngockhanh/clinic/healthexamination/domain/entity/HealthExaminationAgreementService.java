package com.ngockhanh.clinic.healthexamination.domain.entity;

import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.Money;

public record HealthExaminationAgreementService(
    AggregateId id,
    AggregateId serviceId,
    AggregateId agreementId,
    Money referencePriceSnapshot,
    Money negotiatedPrice,
    int displayOrder,
    boolean active,
    long rowVersion) {
  public HealthExaminationAgreementService {
    if (id == null
        || serviceId == null
        || agreementId == null
        || displayOrder < 1
        || rowVersion < 0) throw new IllegalArgumentException("Invalid agreement service");
    validatePrice(referencePriceSnapshot);
    validatePrice(negotiatedPrice);
  }

  private static void validatePrice(Money price) {
    if (price == null
        || !"VND".equals(price.currency())
        || (long) price.amount().precision() - price.amount().scale() > 12)
      throw new IllegalArgumentException("Invalid agreement price");
  }

  public HealthExaminationAgreementService withNegotiatedPrice(Money price) {
    return new HealthExaminationAgreementService(
        id,
        serviceId,
        agreementId,
        referencePriceSnapshot,
        price,
        displayOrder,
        active,
        rowVersion);
  }
}
