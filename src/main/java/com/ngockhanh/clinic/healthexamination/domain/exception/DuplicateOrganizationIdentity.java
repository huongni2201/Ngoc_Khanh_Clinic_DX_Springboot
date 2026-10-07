package com.ngockhanh.clinic.healthexamination.domain.exception;

public final class DuplicateOrganizationIdentity extends DomainException {
  public DuplicateOrganizationIdentity() {
    super("Organization tax code already exists");
  }
}
