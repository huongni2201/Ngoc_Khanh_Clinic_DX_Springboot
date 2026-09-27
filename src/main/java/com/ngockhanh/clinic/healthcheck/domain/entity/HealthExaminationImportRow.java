package com.ngockhanh.clinic.healthcheck.domain.entity;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.AdministrativeSnapshot;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;
import java.util.List;
import java.util.UUID;

public final class HealthExaminationImportRow {
    private final UUID id;
    private final int rowNumber;
    private boolean valid;
    private List<String> errorCodes;
    private final String participantCode;
    private final AdministrativeSnapshot administrativeSnapshot;
    private final String departmentName;
    private final String jobTitle;
    private final String occupation;
    private final IdentificationNumber identificationNumber;
    private final String serviceCode;
    private final UUID serviceRequestId;
    private UUID resolvedParticipantId;
    private UUID resolvedBatchParticipantId;

    private HealthExaminationImportRow(UUID id, int rowNumber, boolean valid, List<String> errorCodes,
                                 String participantCode, AdministrativeSnapshot administrativeSnapshot,
                                 String departmentName, String jobTitle, String occupation,
                                 IdentificationNumber identificationNumber, String serviceCode, UUID serviceRequestId,
                                 UUID resolvedParticipantId, UUID resolvedBatchParticipantId) {
        if (id == null || rowNumber < 1 || (!valid && (errorCodes == null || errorCodes.isEmpty()))) {
            throw new IllegalArgumentException("Invalid import row validation");
        }
        this.id = id;
        this.rowNumber = rowNumber;
        this.valid = valid;
        this.errorCodes = errorCodes == null ? List.of() : List.copyOf(errorCodes);
        this.participantCode = participantCode;
        this.administrativeSnapshot = administrativeSnapshot;
        this.departmentName = departmentName;
        this.jobTitle = jobTitle;
        this.occupation = occupation;
        this.identificationNumber = identificationNumber;
        this.serviceCode = serviceCode;
        this.serviceRequestId = serviceRequestId;
        this.resolvedParticipantId = resolvedParticipantId;
        this.resolvedBatchParticipantId = resolvedBatchParticipantId;
    }

    public static HealthExaminationImportRow roster(UUID id, int rowNumber, String participantCode, AdministrativeSnapshot snapshot) {
        if (participantCode == null || participantCode.isBlank() || snapshot == null) {
            throw new IllegalArgumentException("Roster row requires participant identity and snapshot");
        }
        return roster(id, rowNumber, participantCode, snapshot, null, null, null);
    }

    public static HealthExaminationImportRow roster(UUID id, int rowNumber, String participantCode,
                                                     AdministrativeSnapshot snapshot, String departmentName,
                                                     String jobTitle, String occupation) {
        if (participantCode == null || participantCode.isBlank() || snapshot == null) {
            throw new IllegalArgumentException("Roster row requires participant identity and snapshot");
        }
        return new HealthExaminationImportRow(id, rowNumber, true, List.of(), participantCode, snapshot,
                departmentName, jobTitle, occupation, snapshot.identificationNumber(), null, null, null, null);
    }

    public static HealthExaminationImportRow result(UUID id, int rowNumber, String participantCode, IdentificationNumber identificationNumber,
                                              String serviceCode, UUID serviceRequestId) {
        if ((participantCode == null || participantCode.isBlank()) && identificationNumber == null) {
            throw new IllegalArgumentException("Result row needs exact participant reference");
        }
        if (serviceCode == null || serviceCode.isBlank() || serviceRequestId == null) {
            throw new IllegalArgumentException("Result row needs resolved Service Request");
        }
        return new HealthExaminationImportRow(id, rowNumber, true, List.of(), participantCode, null,
                null, null, null, identificationNumber, serviceCode, serviceRequestId, null, null);
    }

    public static HealthExaminationImportRow invalid(UUID id, int rowNumber, String errorCode) {
        return invalid(id, rowNumber, List.of(errorCode), null, null, null, null, null);
    }

    public static HealthExaminationImportRow invalid(UUID id, int rowNumber, List<String> errorCodes,
                                                      String participantCode, AdministrativeSnapshot snapshot,
                                                      String departmentName, String jobTitle, String occupation) {
        return new HealthExaminationImportRow(id, rowNumber, false, errorCodes, participantCode, snapshot,
                departmentName, jobTitle, occupation,
                snapshot == null ? null : snapshot.identificationNumber(), null, null, null, null);
    }

    public static HealthExaminationImportRow restore(UUID id, int rowNumber, boolean valid, List<String> errorCodes,
                                                       String participantCode, AdministrativeSnapshot snapshot,
                                                       String departmentName, String jobTitle, String occupation,
                                                       IdentificationNumber identificationNumber, String serviceCode,
                                                       UUID serviceRequestId, UUID resolvedParticipantId,
                                                       UUID resolvedBatchParticipantId) {
        return new HealthExaminationImportRow(id, rowNumber, valid, errorCodes, participantCode, snapshot,
                departmentName, jobTitle, occupation, identificationNumber, serviceCode, serviceRequestId,
                resolvedParticipantId, resolvedBatchParticipantId);
    }

    public void reject(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) throw new IllegalArgumentException("Missing import error code");
        valid = false;
        if (!errorCodes.contains(errorCode)) {
            java.util.ArrayList<String> updated = new java.util.ArrayList<>(errorCodes);
            updated.add(errorCode);
            errorCodes = List.copyOf(updated);
        }
    }

    public void resolve(UUID participantId, UUID batchParticipantId) {
        if (!valid || participantId == null || batchParticipantId == null) {
            throw new IllegalArgumentException("Invalid confirmed import row");
        }
        resolvedParticipantId = participantId;
        resolvedBatchParticipantId = batchParticipantId;
    }

    public UUID id() { return id; }
    public int rowNumber() { return rowNumber; }
    public boolean valid() { return valid; }
    public String errorCode() { return errorCodes.isEmpty() ? null : errorCodes.getFirst(); }
    public List<String> errorCodes() { return errorCodes; }
    public String participantCode() { return participantCode; }
    public AdministrativeSnapshot administrativeSnapshot() { return administrativeSnapshot; }
    public String departmentName() { return departmentName; }
    public String jobTitle() { return jobTitle; }
    public String occupation() { return occupation; }
    public IdentificationNumber identificationNumber() { return identificationNumber; }
    public String serviceCode() { return serviceCode; }
    public UUID serviceRequestId() { return serviceRequestId; }
    public UUID resolvedParticipantId() { return resolvedParticipantId; }
    public UUID resolvedBatchParticipantId() { return resolvedBatchParticipantId; }
}
