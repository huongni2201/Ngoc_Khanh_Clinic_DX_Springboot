package com.ngockhanh.clinic.healthcheck.domain.entity;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.AdministrativeSnapshot;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;
import java.util.UUID;

public final class HealthCheckImportRow {
    private final int rowNumber;
    private final boolean valid;
    private final String errorCode;
    private final String employeeCode;
    private final AdministrativeSnapshot administrativeSnapshot;
    private final IdentificationNumber identificationNumber;
    private final String serviceCode;
    private final UUID serviceRequestId;

    private HealthCheckImportRow(int rowNumber, boolean valid, String errorCode, String employeeCode,
                                 AdministrativeSnapshot administrativeSnapshot, IdentificationNumber identificationNumber,
                                 String serviceCode, UUID serviceRequestId) {
        if (rowNumber < 1 || (!valid && (errorCode == null || errorCode.isBlank()))) {
            throw new IllegalArgumentException("Invalid import row validation");
        }
        this.rowNumber = rowNumber;
        this.valid = valid;
        this.errorCode = errorCode;
        this.employeeCode = employeeCode;
        this.administrativeSnapshot = administrativeSnapshot;
        this.identificationNumber = identificationNumber;
        this.serviceCode = serviceCode;
        this.serviceRequestId = serviceRequestId;
    }

    public static HealthCheckImportRow roster(int rowNumber, String employeeCode, AdministrativeSnapshot snapshot) {
        if (employeeCode == null || employeeCode.isBlank() || snapshot == null) {
            throw new IllegalArgumentException("Roster row requires employee identity and snapshot");
        }
        return new HealthCheckImportRow(rowNumber, true, null, employeeCode, snapshot,
                snapshot.identificationNumber(), null, null);
    }

    public static HealthCheckImportRow result(int rowNumber, String employeeCode, IdentificationNumber identificationNumber,
                                              String serviceCode, UUID serviceRequestId) {
        if ((employeeCode == null || employeeCode.isBlank()) && identificationNumber == null) {
            throw new IllegalArgumentException("Result row needs exact employee reference");
        }
        if (serviceCode == null || serviceCode.isBlank() || serviceRequestId == null) {
            throw new IllegalArgumentException("Result row needs resolved Service Request");
        }
        return new HealthCheckImportRow(rowNumber, true, null, employeeCode, null,
                identificationNumber, serviceCode, serviceRequestId);
    }

    public static HealthCheckImportRow invalid(int rowNumber, String errorCode) {
        return new HealthCheckImportRow(rowNumber, false, errorCode, null, null, null, null, null);
    }

    public int rowNumber() { return rowNumber; }
    public boolean valid() { return valid; }
    public String errorCode() { return errorCode; }
    public String employeeCode() { return employeeCode; }
    public AdministrativeSnapshot administrativeSnapshot() { return administrativeSnapshot; }
    public IdentificationNumber identificationNumber() { return identificationNumber; }
    public String serviceCode() { return serviceCode; }
    public UUID serviceRequestId() { return serviceRequestId; }
}
