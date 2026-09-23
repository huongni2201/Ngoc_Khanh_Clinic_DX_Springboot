package com.ngockhanh.clinic.healthcheck.domain.entity;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.AdministrativeSnapshot;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.Cccd;
import java.util.UUID;

public final class HealthCheckImportRow {
    private final UUID id;
    private final int rowNumber;
    private final boolean valid;
    private final String errorCode;
    private final String employeeCode;
    private final AdministrativeSnapshot administrativeSnapshot;
    private final Cccd cccd;
    private final String serviceCode;
    private final UUID serviceRequestId;

    private HealthCheckImportRow(UUID id, int rowNumber, boolean valid, String errorCode, String employeeCode,
                                 AdministrativeSnapshot administrativeSnapshot, Cccd cccd,
                                 String serviceCode, UUID serviceRequestId) {
        if (id == null || rowNumber < 1 || (!valid && (errorCode == null || errorCode.isBlank()))) {
            throw new IllegalArgumentException("Invalid import row validation");
        }
        this.id = id;
        this.rowNumber = rowNumber;
        this.valid = valid;
        this.errorCode = errorCode;
        this.employeeCode = employeeCode;
        this.administrativeSnapshot = administrativeSnapshot;
        this.cccd = cccd;
        this.serviceCode = serviceCode;
        this.serviceRequestId = serviceRequestId;
    }

    public static HealthCheckImportRow roster(UUID id, int rowNumber, String employeeCode, AdministrativeSnapshot snapshot) {
        if (employeeCode == null || employeeCode.isBlank() || snapshot == null) {
            throw new IllegalArgumentException("Roster row requires employee identity and snapshot");
        }
        return new HealthCheckImportRow(id, rowNumber, true, null, employeeCode, snapshot,
                snapshot.cccd(), null, null);
    }

    public static HealthCheckImportRow result(UUID id, int rowNumber, String employeeCode, Cccd cccd,
                                              String serviceCode, UUID serviceRequestId) {
        if ((employeeCode == null || employeeCode.isBlank()) && cccd == null) {
            throw new IllegalArgumentException("Result row needs exact employee reference");
        }
        if (serviceCode == null || serviceCode.isBlank() || serviceRequestId == null) {
            throw new IllegalArgumentException("Result row needs resolved Service Request");
        }
        return new HealthCheckImportRow(id, rowNumber, true, null, employeeCode, null,
                cccd, serviceCode, serviceRequestId);
    }

    public static HealthCheckImportRow invalid(UUID id, int rowNumber, String errorCode) {
        return new HealthCheckImportRow(id, rowNumber, false, errorCode, null, null, null, null, null);
    }

    public UUID id() { return id; }
    public int rowNumber() { return rowNumber; }
    public boolean valid() { return valid; }
    public String errorCode() { return errorCode; }
    public String employeeCode() { return employeeCode; }
    public AdministrativeSnapshot administrativeSnapshot() { return administrativeSnapshot; }
    public Cccd cccd() { return cccd; }
    public String serviceCode() { return serviceCode; }
    public UUID serviceRequestId() { return serviceRequestId; }
}
