package com.ngockhanh.clinic.healthexamination.domain.exception;

public final class AdultEligibilityViolation extends DomainException {
    public AdultEligibilityViolation() { super("Adult health examination requires age 18"); }
}

