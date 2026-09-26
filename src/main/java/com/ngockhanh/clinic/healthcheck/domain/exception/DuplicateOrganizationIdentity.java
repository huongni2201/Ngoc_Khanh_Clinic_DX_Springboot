package com.ngockhanh.clinic.healthcheck.domain.exception;

public final class DuplicateOrganizationIdentity extends DomainException {
    public DuplicateOrganizationIdentity() {
        super("Organization code or tax code already exists");
    }
}
