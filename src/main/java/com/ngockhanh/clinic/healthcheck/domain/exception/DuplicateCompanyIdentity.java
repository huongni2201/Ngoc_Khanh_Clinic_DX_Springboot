package com.ngockhanh.clinic.healthcheck.domain.exception;

public final class DuplicateCompanyIdentity extends DomainException {
    public DuplicateCompanyIdentity() {
        super("Company code or tax code is already registered");
    }
}