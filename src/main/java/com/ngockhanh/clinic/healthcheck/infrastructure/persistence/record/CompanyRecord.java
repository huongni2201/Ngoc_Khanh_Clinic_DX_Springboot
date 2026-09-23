package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record;

import java.time.LocalDateTime;
import java.util.UUID;

public record CompanyRecord(
        UUID id,
        String companyCode,
        String companyName,
        String taxCode,
        String address,
        String contactName,
        String contactPhone,
        String contactJobTitle,
        String note,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        byte[] rowVersion
) {
}
