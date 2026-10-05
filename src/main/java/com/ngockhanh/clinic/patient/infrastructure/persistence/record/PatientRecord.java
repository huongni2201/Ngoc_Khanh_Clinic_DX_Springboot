package com.ngockhanh.clinic.patient.infrastructure.persistence.record;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Persistence row of {@code public.patients}. */
public record PatientRecord(
    UUID id,
    String patientCode,
    String fullName,
    LocalDate dateOfBirth,
    String sex,
    String identificationNumber,
    String phone,
    String email,
    String address,
    String status,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {}
