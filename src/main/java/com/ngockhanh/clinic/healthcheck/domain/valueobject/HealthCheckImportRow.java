package com.ngockhanh.clinic.healthcheck.domain.valueobject;

import java.util.UUID;

public record HealthCheckImportRow(Integer rowNumber, Boolean valid, String errorCode,
                                   String employeeCode, AdministrativeSnapshot administrativeSnapshot,
                                   IdentificationNumber identificationNumber, String serviceCode, UUID serviceRequestId) {
    public HealthCheckImportRow(Integer rowNumber, Boolean valid, String errorCode) {
        this(rowNumber, valid, errorCode, null, null, null, null, null);
    }

    public HealthCheckImportRow(Integer rowNumber, String employeeCode, AdministrativeSnapshot administrativeSnapshot) {
        this(rowNumber, true, null, employeeCode, administrativeSnapshot, null, null, null);
    }

    public static HealthCheckImportRow result(Integer rowNumber, String employeeCode, IdentificationNumber identificationNumber,
                                               String serviceCode, UUID serviceRequestId) {
        if ((employeeCode == null || employeeCode.isBlank()) && identificationNumber == null) {
            throw new IllegalArgumentException("Result row needs exact employee reference");
        }
        if (serviceCode == null || serviceCode.isBlank() || serviceRequestId == null || serviceRequestId == null) {
            throw new IllegalArgumentException("Result row needs resolved Service Request");
        }
        return new HealthCheckImportRow(rowNumber, true, null, employeeCode, null, identificationNumber, serviceCode, serviceRequestId);
    }

    public HealthCheckImportRow {
        if (rowNumber < 1 || (!valid && (errorCode == null || errorCode.isBlank()))
                || (administrativeSnapshot != null && (employeeCode == null || employeeCode.isBlank()))
                || (serviceRequestId != null && serviceRequestId == null)) {
            throw new IllegalArgumentException("Invalid import row validation");
        }
    }
}
