package com.ngockhanh.clinic.healthcheck.domain.entity;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.AdministrativeSnapshot;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;
import java.util.UUID;

public final class HealthExaminationImportRow {
    private final UUID id;
    private final int rowNumber;
    private final boolean valid;
    private final String errorCode;
    private final String participantCode;
    private final AdministrativeSnapshot administrativeSnapshot;
    private final IdentificationNumber identificationNumber;
    private final String serviceCode;
    private final UUID serviceRequestId;

    private HealthExaminationImportRow(UUID id, int rowNumber, boolean valid, String errorCode, String participantCode,
                                 AdministrativeSnapshot administrativeSnapshot, IdentificationNumber identificationNumber,
                                 String serviceCode, UUID serviceRequestId) {
        if (id == null || rowNumber < 1 || (!valid && (errorCode == null || errorCode.isBlank()))) {
            throw new IllegalArgumentException("Invalid import row validation");
        }
        this.id = id;
        this.rowNumber = rowNumber;
        this.valid = valid;
        this.errorCode = errorCode;
        this.participantCode = participantCode;
        this.administrativeSnapshot = administrativeSnapshot;
        this.identificationNumber = identificationNumber;
        this.serviceCode = serviceCode;
        this.serviceRequestId = serviceRequestId;
    }

    public static HealthExaminationImportRow roster(UUID id, int rowNumber, String participantCode, AdministrativeSnapshot snapshot) {
        if (participantCode == null || participantCode.isBlank() || snapshot == null) {
            throw new IllegalArgumentException("Roster row requires participant identity and snapshot");
        }
        return new HealthExaminationImportRow(id, rowNumber, true, null, participantCode, snapshot,
                snapshot.identificationNumber(), null, null);
    }

    public static HealthExaminationImportRow result(UUID id, int rowNumber, String participantCode, IdentificationNumber identificationNumber,
                                              String serviceCode, UUID serviceRequestId) {
        if ((participantCode == null || participantCode.isBlank()) && identificationNumber == null) {
            throw new IllegalArgumentException("Result row needs exact participant reference");
        }
        if (serviceCode == null || serviceCode.isBlank() || serviceRequestId == null) {
            throw new IllegalArgumentException("Result row needs resolved Service Request");
        }
        return new HealthExaminationImportRow(id, rowNumber, true, null, participantCode, null,
                identificationNumber, serviceCode, serviceRequestId);
    }

    public static HealthExaminationImportRow invalid(UUID id, int rowNumber, String errorCode) {
        return new HealthExaminationImportRow(id, rowNumber, false, errorCode, null, null, null, null, null);
    }

    public UUID id() { return id; }
    public int rowNumber() { return rowNumber; }
    public boolean valid() { return valid; }
    public String errorCode() { return errorCode; }
    public String participantCode() { return participantCode; }
    public AdministrativeSnapshot administrativeSnapshot() { return administrativeSnapshot; }
    public IdentificationNumber identificationNumber() { return identificationNumber; }
    public String serviceCode() { return serviceCode; }
    public UUID serviceRequestId() { return serviceRequestId; }
}
