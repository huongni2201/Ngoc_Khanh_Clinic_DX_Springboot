package com.ngockhanh.clinic.healthexamination.domain.exception;

public final class ServiceOutsideAgreementScope extends DomainException {
  public ServiceOutsideAgreementScope() {
    super("Service outside agreement scope");
  }
}
