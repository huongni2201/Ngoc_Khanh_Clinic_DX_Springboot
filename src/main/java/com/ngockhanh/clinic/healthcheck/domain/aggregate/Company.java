package com.ngockhanh.clinic.healthcheck.domain.aggregate;

import java.util.UUID;

public record Company(UUID id, String code, String name, String contactName, String contactPhone) {
    public Company(String code, String name, String contactName, String contactPhone) {
        this(null, code, name, contactName, contactPhone);
    }

    public Company {
        if (id == null || code == null || code.isBlank() || name == null || name.isBlank()
                || contactName == null || contactName.isBlank() || contactPhone == null || contactPhone.isBlank()) {
            throw new IllegalArgumentException("Missing company details");
        }
    }
}
