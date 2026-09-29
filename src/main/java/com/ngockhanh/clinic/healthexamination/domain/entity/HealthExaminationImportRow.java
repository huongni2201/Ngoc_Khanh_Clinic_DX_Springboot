package com.ngockhanh.clinic.healthexamination.domain.entity;

import java.time.LocalDate;
import java.util.List;

import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;

public final class HealthExaminationImportRow {
    private final AggregateId id;
    private final int rowNumber;
    private boolean valid;
    private List<String> errorCodes;
    private final String participantCode;
    private final String fullName;
    private final LocalDate dateOfBirth;
    private final String sex;
    private final IdentificationNumber identificationNumber;
    private final LocalDate identificationNumberIssueDate;
    private final String identificationNumberIssuePlace;
    private final String ethnicity;
    private final String subjectType;
    private final String payerSource;
    private final String bloodGroup;
    private final String phone;
    private final String province;
    private final String ward;
    private final String addressDetail;
    private final String administrativeOccupation;
    private final String workplaceOrSchool;
    private final String healthExaminationReason;
    private final String departmentName;
    private final String jobTitle;
    private final String occupation;
    private final String serviceCode;
    private final AggregateId serviceRequestId;
    private AggregateId resolvedPatientId;
    private AggregateId resolvedParticipantId;
    private AggregateId resolvedBatchParticipantId;
    private AggregateId resolvedBatchServiceId;

    private HealthExaminationImportRow(AggregateId id, int rowNumber, boolean valid, List<String> errorCodes,
                                       String participantCode, String fullName, LocalDate dateOfBirth, String sex,
                                       IdentificationNumber identificationNumber,
                                       LocalDate identificationNumberIssueDate,
                                       String identificationNumberIssuePlace, String ethnicity, String subjectType,
                                       String payerSource, String bloodGroup, String phone, String province,
                                       String ward, String addressDetail, String administrativeOccupation,
                                       String workplaceOrSchool, String healthExaminationReason,
                                       String departmentName, String jobTitle, String occupation, String serviceCode,
                                       AggregateId serviceRequestId, AggregateId resolvedParticipantId,
                                       AggregateId resolvedBatchParticipantId) {
        this(id, rowNumber, valid, errorCodes, participantCode, fullName, dateOfBirth, sex,
                identificationNumber, identificationNumberIssueDate, identificationNumberIssuePlace, ethnicity,
                subjectType, payerSource, bloodGroup, phone, province, ward, addressDetail,
                administrativeOccupation, workplaceOrSchool, healthExaminationReason, departmentName, jobTitle,
                occupation, serviceCode, serviceRequestId, null, resolvedParticipantId,
                resolvedBatchParticipantId, null);
    }

    private HealthExaminationImportRow(AggregateId id, int rowNumber, boolean valid, List<String> errorCodes,
                                       String participantCode, String fullName, LocalDate dateOfBirth, String sex,
                                       IdentificationNumber identificationNumber,
                                       LocalDate identificationNumberIssueDate,
                                       String identificationNumberIssuePlace, String ethnicity, String subjectType,
                                       String payerSource, String bloodGroup, String phone, String province,
                                       String ward, String addressDetail, String administrativeOccupation,
                                       String workplaceOrSchool, String healthExaminationReason,
                                       String departmentName, String jobTitle, String occupation, String serviceCode,
                                       AggregateId serviceRequestId, AggregateId resolvedPatientId,
                                       AggregateId resolvedParticipantId, AggregateId resolvedBatchParticipantId,
                                       AggregateId resolvedBatchServiceId) {
        if (id == null || rowNumber < 1 || (!valid && (errorCodes == null || errorCodes.isEmpty()))) {
            throw new IllegalArgumentException("Invalid import row validation");
        }
        this.id = id;
        this.rowNumber = rowNumber;
        this.valid = valid;
        this.errorCodes = errorCodes == null ? List.of() : List.copyOf(errorCodes);
        this.participantCode = participantCode;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.sex = sex;
        this.identificationNumber = identificationNumber;
        this.identificationNumberIssueDate = identificationNumberIssueDate;
        this.identificationNumberIssuePlace = identificationNumberIssuePlace;
        this.ethnicity = ethnicity;
        this.subjectType = subjectType;
        this.payerSource = payerSource;
        this.bloodGroup = bloodGroup;
        this.phone = phone;
        this.province = province;
        this.ward = ward;
        this.addressDetail = addressDetail;
        this.administrativeOccupation = administrativeOccupation;
        this.workplaceOrSchool = workplaceOrSchool;
        this.healthExaminationReason = healthExaminationReason;
        this.departmentName = departmentName;
        this.jobTitle = jobTitle;
        this.occupation = occupation;
        this.serviceCode = serviceCode;
        this.serviceRequestId = serviceRequestId;
        this.resolvedPatientId = resolvedPatientId;
        this.resolvedParticipantId = resolvedParticipantId;
        this.resolvedBatchParticipantId = resolvedBatchParticipantId;
        this.resolvedBatchServiceId = resolvedBatchServiceId;
    }

    public static HealthExaminationImportRow roster(AggregateId id, int rowNumber, String participantCode,
                                                    String fullName, LocalDate dateOfBirth, String sex,
                                                    IdentificationNumber identificationNumber) {
        return roster(id, rowNumber, participantCode, fullName, dateOfBirth, sex, identificationNumber,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    public static HealthExaminationImportRow roster(AggregateId id, int rowNumber, String participantCode,
                                                    String fullName, LocalDate dateOfBirth, String sex,
                                                    IdentificationNumber identificationNumber,
                                                    LocalDate identificationNumberIssueDate,
                                                    String identificationNumberIssuePlace, String ethnicity,
                                                    String subjectType, String payerSource, String bloodGroup,
                                                    String phone, String province, String ward, String addressDetail,
                                                    String administrativeOccupation, String workplaceOrSchool,
                                                    String healthExaminationReason, String departmentName,
                                                    String jobTitle, String occupation) {
        if (participantCode == null || participantCode.isBlank() || fullName == null || fullName.isBlank()
                || dateOfBirth == null || sex == null || sex.isBlank() || identificationNumber == null) {
            throw new IllegalArgumentException("Roster row requires participant identity fields");
        }
        return new HealthExaminationImportRow(id, rowNumber, true, List.of(), participantCode, fullName,
                dateOfBirth, sex, identificationNumber, identificationNumberIssueDate,
                identificationNumberIssuePlace, ethnicity, subjectType, payerSource, bloodGroup, phone, province,
                ward, addressDetail, administrativeOccupation, workplaceOrSchool, healthExaminationReason,
                departmentName, jobTitle, occupation, null, null, null, null);
    }

    public static HealthExaminationImportRow result(AggregateId id, int rowNumber, String participantCode,
                                                    IdentificationNumber identificationNumber, String serviceCode,
                                                    AggregateId serviceRequestId) {
        if ((participantCode == null || participantCode.isBlank()) && identificationNumber == null) {
            throw new IllegalArgumentException("Result row needs exact participant reference");
        }
        if (serviceCode == null || serviceCode.isBlank() || serviceRequestId == null) {
            throw new IllegalArgumentException("Result row needs resolved Service Request");
        }
        return new HealthExaminationImportRow(id, rowNumber, true, List.of(), participantCode, null, null, null,
                identificationNumber, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, serviceCode, serviceRequestId, null, null);
    }

    public static HealthExaminationImportRow invalid(AggregateId id, int rowNumber, String errorCode) {
        return invalid(id, rowNumber, List.of(errorCode), null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public static HealthExaminationImportRow invalid(AggregateId id, int rowNumber, List<String> errorCodes,
                                                     String participantCode, String fullName, LocalDate dateOfBirth,
                                                     String sex, IdentificationNumber identificationNumber,
                                                     LocalDate identificationNumberIssueDate,
                                                     String identificationNumberIssuePlace, String ethnicity,
                                                     String subjectType, String payerSource, String bloodGroup,
                                                     String phone, String province, String ward, String addressDetail,
                                                     String administrativeOccupation, String workplaceOrSchool,
                                                     String healthExaminationReason, String departmentName,
                                                     String jobTitle, String occupation) {
        return new HealthExaminationImportRow(id, rowNumber, false, errorCodes, participantCode, fullName,
                dateOfBirth, sex, identificationNumber, identificationNumberIssueDate,
                identificationNumberIssuePlace, ethnicity, subjectType, payerSource, bloodGroup, phone, province,
                ward, addressDetail, administrativeOccupation, workplaceOrSchool, healthExaminationReason,
                departmentName, jobTitle, occupation, null, null, null, null);
    }

    public static HealthExaminationImportRow restore(AggregateId id, int rowNumber, boolean valid,
                                                      List<String> errorCodes, String participantCode,
                                                      String fullName, LocalDate dateOfBirth, String sex,
                                                      IdentificationNumber identificationNumber,
                                                      LocalDate identificationNumberIssueDate,
                                                      String identificationNumberIssuePlace, String ethnicity,
                                                      String subjectType, String payerSource, String bloodGroup,
                                                      String phone, String province, String ward, String addressDetail,
                                                      String administrativeOccupation, String workplaceOrSchool,
                                                      String healthExaminationReason, String departmentName,
                                                      String jobTitle, String occupation, String serviceCode,
                                                      AggregateId serviceRequestId, AggregateId resolvedParticipantId,
                                                      AggregateId resolvedBatchParticipantId) {
        return new HealthExaminationImportRow(id, rowNumber, valid, errorCodes, participantCode, fullName,
                dateOfBirth, sex, identificationNumber, identificationNumberIssueDate,
                identificationNumberIssuePlace, ethnicity, subjectType, payerSource, bloodGroup, phone, province,
                ward, addressDetail, administrativeOccupation, workplaceOrSchool, healthExaminationReason,
                departmentName, jobTitle, occupation, serviceCode, serviceRequestId, resolvedParticipantId,
                resolvedBatchParticipantId);
    }

    public static HealthExaminationImportRow restore(AggregateId id, int rowNumber, boolean valid,
                                                      List<String> errorCodes, String participantCode,
                                                      String fullName, LocalDate dateOfBirth, String sex,
                                                      IdentificationNumber identificationNumber,
                                                      LocalDate identificationNumberIssueDate,
                                                      String identificationNumberIssuePlace, String ethnicity,
                                                      String subjectType, String payerSource, String bloodGroup,
                                                      String phone, String province, String ward, String addressDetail,
                                                      String administrativeOccupation, String workplaceOrSchool,
                                                      String healthExaminationReason, String departmentName,
                                                      String jobTitle, String occupation, String serviceCode,
                                                      AggregateId serviceRequestId, AggregateId resolvedPatientId,
                                                      AggregateId resolvedParticipantId,
                                                      AggregateId resolvedBatchParticipantId,
                                                      AggregateId resolvedBatchServiceId) {
        return new HealthExaminationImportRow(id, rowNumber, valid, errorCodes, participantCode, fullName,
                dateOfBirth, sex, identificationNumber, identificationNumberIssueDate,
                identificationNumberIssuePlace, ethnicity, subjectType, payerSource, bloodGroup, phone, province,
                ward, addressDetail, administrativeOccupation, workplaceOrSchool, healthExaminationReason,
                departmentName, jobTitle, occupation, serviceCode, serviceRequestId, resolvedPatientId,
                resolvedParticipantId, resolvedBatchParticipantId, resolvedBatchServiceId);
    }

    public boolean hasAdministrativeIdentity() {
        return fullName != null && !fullName.isBlank() && dateOfBirth != null
                && sex != null && !sex.isBlank() && identificationNumber != null;
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

    public void resolve(AggregateId participantId, AggregateId batchParticipantId) {
        if (!valid || participantId == null || batchParticipantId == null) {
            throw new IllegalArgumentException("Invalid confirmed import row");
        }
        resolvedParticipantId = participantId;
        resolvedBatchParticipantId = batchParticipantId;
    }

    public void resolve(AggregateId patientId, AggregateId participantId,
                        AggregateId batchParticipantId, AggregateId batchServiceId) {
        if (!valid || patientId == null || participantId == null || batchParticipantId == null
                || batchServiceId == null) {
            throw new IllegalArgumentException("Invalid confirmed import row resolution");
        }
        resolve(participantId, batchParticipantId);
        resolvedPatientId = patientId;
        resolvedBatchServiceId = batchServiceId;
    }

    public AggregateId id() { return id; }
    public int rowNumber() { return rowNumber; }
    public boolean valid() { return valid; }
    public String errorCode() { return errorCodes.isEmpty() ? null : errorCodes.getFirst(); }
    public List<String> errorCodes() { return errorCodes; }
    public String participantCode() { return participantCode; }
    public String fullName() { return fullName; }
    public LocalDate dateOfBirth() { return dateOfBirth; }
    public String sex() { return sex; }
    public IdentificationNumber identificationNumber() { return identificationNumber; }
    public LocalDate identificationNumberIssueDate() { return identificationNumberIssueDate; }
    public String identificationNumberIssuePlace() { return identificationNumberIssuePlace; }
    public String ethnicity() { return ethnicity; }
    public String subjectType() { return subjectType; }
    public String payerSource() { return payerSource; }
    public String bloodGroup() { return bloodGroup; }
    public String phone() { return phone; }
    public String province() { return province; }
    public String ward() { return ward; }
    public String addressDetail() { return addressDetail; }
    public String administrativeOccupation() { return administrativeOccupation; }
    public String workplaceOrSchool() { return workplaceOrSchool; }
    public String healthExaminationReason() { return healthExaminationReason; }
    public String departmentName() { return departmentName; }
    public String jobTitle() { return jobTitle; }
    public String occupation() { return occupation; }
    public String serviceCode() { return serviceCode; }
    public AggregateId serviceRequestId() { return serviceRequestId; }
    public AggregateId resolvedPatientId() { return resolvedPatientId; }
    public AggregateId resolvedParticipantId() { return resolvedParticipantId; }
    public AggregateId resolvedBatchParticipantId() { return resolvedBatchParticipantId; }
    public AggregateId resolvedBatchServiceId() { return resolvedBatchServiceId; }
}
