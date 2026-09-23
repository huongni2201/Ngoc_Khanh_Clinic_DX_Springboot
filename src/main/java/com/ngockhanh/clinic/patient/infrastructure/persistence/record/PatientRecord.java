package com.ngockhanh.clinic.patient.infrastructure.persistence.record;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PatientRecord(
        UUID id,
        String patientCode,
        String cccd,
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
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        byte[] rowVersion
) {
}
