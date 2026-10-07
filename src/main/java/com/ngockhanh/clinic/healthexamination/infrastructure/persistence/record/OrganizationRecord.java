package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record OrganizationRecord(
    UUID id,
    String name,
    String taxCode,
    String phone,
    String email,
    String address,
    String contactFullName,
    String contactPhone,
    String contactEmail,
    String status,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {}
