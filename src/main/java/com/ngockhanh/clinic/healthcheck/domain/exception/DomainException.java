package com.ngockhanh.clinic.healthcheck.domain.exception;

import com.ngockhanh.clinic.shared.exception.BusinessRuleException;

public abstract class DomainException extends BusinessRuleException {
    protected DomainException(String message) {
        super(message);
    }
}