package com.ngockhanh.clinic.appointment.infrastructure.persistence.record;

import java.time.Instant;
import java.util.UUID;

/** Persistence row of {@code public.appointments}. */
public record AppointmentRecord(
    UUID id,
    UUID patientId,
    UUID sourceEncounterId,
    UUID doctorId,
    UUID departmentId,
    UUID roomId,
    UUID serviceId,
    Instant scheduledStart,
    Instant scheduledEnd,
    String status,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {}
