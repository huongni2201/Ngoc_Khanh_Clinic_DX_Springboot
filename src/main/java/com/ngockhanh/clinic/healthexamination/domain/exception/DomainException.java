package com.ngockhanh.clinic.healthexamination.domain.exception;

import com.ngockhanh.clinic.shared.exception.BusinessRuleException;

public abstract class DomainException extends BusinessRuleException {
    protected DomainException(String message) {
        super(message);
    }
}