package com.ngockhanh.clinic.healthexamination.application.usecase;

final class OrganizationFieldNormalizer {
    private OrganizationFieldNormalizer() {
    }

    static String required(String value) {
        return value == null ? null : value.trim();
    }

    static String optional(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
