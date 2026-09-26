package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

public record OrganizationRecord(
        UUID id,
        String organizationCode,
        String organizationName,
        String taxCode,
        String address,
        String contactName,
        String contactPhone,
        String contactJobTitle,
        String note,
        String status,
        Instant createdAt,
        Instant updatedAt,
        long rowVersion
) {
}
