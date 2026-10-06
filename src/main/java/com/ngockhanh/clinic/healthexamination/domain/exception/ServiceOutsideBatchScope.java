package com.ngockhanh.clinic.healthexamination.domain.exception;

public final class ServiceOutsideBatchScope extends DomainException {
    public ServiceOutsideBatchScope() { super("Service outside batch scope"); }
}
