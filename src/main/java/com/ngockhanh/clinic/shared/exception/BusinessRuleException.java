package com.ngockhanh.clinic.shared.exception;

public abstract class BusinessRuleException extends RuntimeException {
    protected BusinessRuleException(String message) {
        super(message);
    }
}