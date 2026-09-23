package com.ngockhanh.clinic.shared.exception;

public final class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource) {
        super("Requested resource not found");
    }
}
