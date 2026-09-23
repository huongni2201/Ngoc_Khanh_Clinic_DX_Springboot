package com.ngockhanh.clinic.healthcheck.domain.valueobject;

public record IdentificationNumber(String value) {
    public IdentificationNumber {
        if (value == null || value.isBlank() || value.length() > 20 || !value.matches("[0-9]+")) {
            throw new IllegalArgumentException("Invalid Identification Number");
        }
    }

    public static IdentificationNumber of(String value) {
        return new IdentificationNumber(value);
    }
}
