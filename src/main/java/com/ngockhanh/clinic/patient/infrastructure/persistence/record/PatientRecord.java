package com.ngockhanh.clinic.patient.infrastructure.persistence.record;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

public record PatientRecord(
        UUID id,
        String patientCode,
        String identificationNumber,
        String fullName,
        String fullNameNormalized,
        LocalDate dateOfBirth,
        String sex,
        String phone,
        String email,
        String address,
        String wardCode,
        String provinceCode,
        String occupation,
        String note,
        String status,
        Instant createdAt,
        Instant updatedAt,
        long rowVersion
) {
}
