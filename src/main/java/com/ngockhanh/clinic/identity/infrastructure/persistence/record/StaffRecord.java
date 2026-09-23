package com.ngockhanh.clinic.identity.infrastructure.persistence.record;

import java.util.UUID;

public record StaffRecord(
        UUID id,
        String staffCode,
        String fullName,
        String staffType,
        String licenseNumber,
        String phone,
        Boolean isActive
) {
}
