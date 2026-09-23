package com.ngockhanh.clinic.shared.exception;

public final class ConcurrentUpdateException extends RuntimeException {
    public ConcurrentUpdateException() {
        super("Record was changed by another request");
    }
}
