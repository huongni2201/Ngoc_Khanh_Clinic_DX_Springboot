package com.ngockhanh.clinic.healthcheck.domain.exception;

public final class DomainRuleViolation extends DomainException {
    public DomainRuleViolation(String message) {
        super(message);
    }
}
