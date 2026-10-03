package com.ngockhanh.clinic.shared.exception;

public final class ConcurrentUpdateException extends RuntimeException {
    private final String errorCode;

    public ConcurrentUpdateException() {
        this("CONCURRENT_UPDATE", "Record was changed by another request");
    }

    public ConcurrentUpdateException(String errorCode, String message) {
        super(message);
        if (errorCode == null || errorCode.isBlank()) throw new IllegalArgumentException("Missing error code");
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
