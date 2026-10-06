package com.ngockhanh.clinic.accesscontrol.infrastructure.persistence.record;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Persistence row of {@code public.staff_members}. */
public record StaffMemberRecord(
    UUID id,
    String staffCode,
    String fullName,
    LocalDate dateOfBirth,
    String sex,
    String phone,
    String email,
    UUID primaryDepartmentId,
    UUID roomId,
    String status,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {}
