package com.ngockhanh.clinic.healthcheck.domain.exception;

public final class AdultEligibilityViolation extends DomainException {
    public AdultEligibilityViolation() { super("Adult health check requires age 18"); }
}

