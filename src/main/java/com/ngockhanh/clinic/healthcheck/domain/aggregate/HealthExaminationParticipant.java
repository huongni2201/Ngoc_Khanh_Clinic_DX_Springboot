package com.ngockhanh.clinic.healthcheck.domain.aggregate;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthcheck.domain.exception.PatientRelinkForbidden;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.IdentificationNumber;

public final class HealthExaminationParticipant {
    private final UUID id;
    private final UUID organizationId;
    private final String participantCode;
    private final IdentificationNumber identificationNumber;
    private final String fullName;
    private final LocalDate dateOfBirth;
    private final String sex;
    private final String departmentName;
    private final String jobTitle;
    private final String occupation;
    private final String status;
    private UUID patientId;

    private HealthExaminationParticipant(UUID id, UUID organizationId, String participantCode,
                                         IdentificationNumber identificationNumber, String fullName,
                                         LocalDate dateOfBirth, String sex, String departmentName,
                                         String jobTitle, String occupation, String status) {
        if (id == null || organizationId == null || participantCode == null || participantCode.isBlank()
                || identificationNumber == null || fullName == null || fullName.isBlank()
                || dateOfBirth == null || sex == null || sex.isBlank() || status == null || status.isBlank()) {
            throw new IllegalArgumentException("Missing health-examination participant identity");
        }
        this.id = id;
        this.organizationId = organizationId;
        this.participantCode = participantCode;
        this.identificationNumber = identificationNumber;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.sex = sex;
        this.departmentName = departmentName;
        this.jobTitle = jobTitle;
        this.occupation = occupation;
        this.status = status;
    }

    public static HealthExaminationParticipant create(UUID id, UUID organizationId, String participantCode,
                                                      IdentificationNumber identificationNumber, String fullName,
                                                      LocalDate dateOfBirth, String sex) {
        return create(id, organizationId, participantCode, identificationNumber, fullName, dateOfBirth, sex,
                null, null, null);
    }

    public static HealthExaminationParticipant create(UUID id, UUID organizationId, String participantCode,
                                                      IdentificationNumber identificationNumber, String fullName,
                                                      LocalDate dateOfBirth, String sex, String departmentName,
                                                      String jobTitle, String occupation) {
        return new HealthExaminationParticipant(id, organizationId, participantCode, identificationNumber, fullName,
                dateOfBirth, sex, departmentName, jobTitle, occupation, "ACTIVE");
    }

    public static HealthExaminationParticipant restore(UUID id, UUID organizationId, String participantCode,
                                                       IdentificationNumber identificationNumber, String fullName,
                                                       LocalDate dateOfBirth, String sex, String departmentName,
                                                       String jobTitle, String occupation, String status,
                                                       UUID patientId) {
        HealthExaminationParticipant participant = new HealthExaminationParticipant(id, organizationId, participantCode,
                identificationNumber, fullName, dateOfBirth, sex, departmentName, jobTitle, occupation, status);
        participant.patientId = patientId;
        return participant;
    }

    public static HealthExaminationParticipant restore(UUID id, UUID organizationId, String participantCode,
                                                       IdentificationNumber identificationNumber, String fullName,
                                                       LocalDate dateOfBirth, String sex, UUID patientId) {
        return restore(id, organizationId, participantCode, identificationNumber, fullName, dateOfBirth, sex,
                null, null, null, "ACTIVE", patientId);
    }

    public void linkPatient(UUID patientId) {
        if (patientId == null) throw new IllegalArgumentException("Invalid Patient ID");
        if (this.patientId != null && !Objects.equals(this.patientId, patientId)) throw new PatientRelinkForbidden();
        this.patientId = patientId;
    }

    public HealthExaminationParticipant reimport(String participantCode, IdentificationNumber identificationNumber,
                                                 String fullName, LocalDate dateOfBirth, String sex) {
        return reimport(participantCode, identificationNumber, fullName, dateOfBirth, sex,
                departmentName, jobTitle, occupation);
    }

    public HealthExaminationParticipant reimport(String participantCode, IdentificationNumber identificationNumber,
                                                 String fullName, LocalDate dateOfBirth, String sex,
                                                 String departmentName, String jobTitle, String occupation) {
        if (patientId != null && (!this.participantCode.equals(participantCode)
                || !this.identificationNumber.equals(identificationNumber))) {
            throw new DomainRuleViolation("Linked participant identity requires review");
        }
        HealthExaminationParticipant updated = new HealthExaminationParticipant(id, organizationId, participantCode,
                identificationNumber, fullName, dateOfBirth, sex, departmentName, jobTitle, occupation, status);
        updated.patientId = patientId;
        return updated;
    }

    public UUID id() { return id; }
    public UUID organizationId() { return organizationId; }
    public String participantCode() { return participantCode; }
    public IdentificationNumber identificationNumber() { return identificationNumber; }
    public String fullName() { return fullName; }
    public LocalDate dateOfBirth() { return dateOfBirth; }
    public String sex() { return sex; }
    public String departmentName() { return departmentName; }
    public String jobTitle() { return jobTitle; }
    public String occupation() { return occupation; }
    public String status() { return status; }
    public UUID patientId() { return patientId; }
}
