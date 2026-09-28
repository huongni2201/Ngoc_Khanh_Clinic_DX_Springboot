package com.ngockhanh.clinic.healthexamination.domain.valueobject;

public record ShsCode(String value) {
    public ShsCode {
        if (value == null || value.isBlank() || value.length() > 40) {
            throw new IllegalArgumentException("Invalid SHS code");
        }
    }

    public static ShsCode of(String value) {
        return new ShsCode(value);
    }
}
