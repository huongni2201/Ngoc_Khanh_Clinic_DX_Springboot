package com.ngockhanh.clinic.encounter.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Persistence row of {@code public.encounters}. */
@Builder
public record EncounterRecord(
    UUID id,
    UUID patientId,
    String encounterType,
    String status,
    UUID appointmentId,
    UUID assignedRoomId,
    UUID assignedDoctorId,
    Instant checkedInAt,
    Instant startedAt,
    Instant completedAt,
    Instant cancelledAt,
    String cancelReason,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {}
