package com.ngockhanh.clinic.healthcheck.infrastructure.persistence.record;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CompanyEmployeeRecord(
        UUID id,
        UUID companyId,
        UUID patientId,
        String employeeCode,
        String identificationNumber,
        String fullName,
        LocalDate dateOfBirth,
        String sex,
        String departmentName,
        String jobTitle,
        String occupation,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
