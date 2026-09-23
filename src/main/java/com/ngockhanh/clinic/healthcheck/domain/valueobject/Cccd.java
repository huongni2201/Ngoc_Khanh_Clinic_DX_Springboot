package com.ngockhanh.clinic.healthcheck.domain.valueobject;

public record Cccd(String value) {
    public Cccd {
        if (value == null || value.isBlank() || value.length() > 20 || !value.matches("[0-9]+")) {
            throw new IllegalArgumentException("Invalid CCCD");
        }
    }

    public static Cccd of(String value) {
        return new Cccd(value);
    }
}
