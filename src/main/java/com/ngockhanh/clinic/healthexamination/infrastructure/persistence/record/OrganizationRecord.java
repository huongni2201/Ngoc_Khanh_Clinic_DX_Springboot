package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

public record OrganizationRecord(
    UUID id,
    String code,
    String name,
    String organizationType,
    String taxCode,
    String phone,
    String email,
    String address,
    String contactFullName,
    String contactPosition,
    String contactPhone,
    String contactEmail,
    String status,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {}
