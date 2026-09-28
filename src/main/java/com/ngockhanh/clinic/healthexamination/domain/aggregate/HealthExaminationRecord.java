package com.ngockhanh.clinic.healthexamination.domain.aggregate;

import java.time.LocalDate;

import com.ngockhanh.clinic.healthexamination.domain.enums.HealthExaminationRecordStatus;
import com.ngockhanh.clinic.healthexamination.domain.exception.AdultEligibilityViolation;
import com.ngockhanh.clinic.healthexamination.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.IdentificationNumber;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ShsCode;

public final class HealthExaminationRecord {
    private final AggregateId id;
    private final AggregateId batchParticipantId;
    private final ShsCode shs;
    private final AggregateId patientId;
    private final AggregateId encounterId;
    private final String fullNameSnapshot;
    private final LocalDate dateOfBirthSnapshot;
    private final String sexSnapshot;
    private final IdentificationNumber identificationNumberSnapshot;
    private final LocalDate identificationNumberIssueDateSnapshot;
    private final String identificationNumberIssuePlaceSnapshot;
    private final String ethnicitySnapshot;
    private final String subjectTypeSnapshot;
    private final String payerSourceSnapshot;
    private final String bloodGroupSnapshot;
    private final String phoneSnapshot;
    private final String provinceSnapshot;
    private final String wardSnapshot;
    private final String addressDetailSnapshot;
    private final String occupationSnapshot;
    private final String workplaceOrSchoolSnapshot;
    private final String healthExaminationReasonSnapshot;
    private final LocalDate plannedExaminationDate;
    private final AggregateId masterTemplateVersionId;
    private final AggregateId replacesHealthExaminationRecordId;
    private LocalDate actualExaminationDate;
    private HealthExaminationRecordStatus status;

    private HealthExaminationRecord(AggregateId id, ShsCode shs, AggregateId patientId,
                                    AggregateId encounterId, AggregateId batchParticipantId,
                                    String fullNameSnapshot, LocalDate dateOfBirthSnapshot,
                                    String sexSnapshot, IdentificationNumber identificationNumberSnapshot,
                                    LocalDate identificationNumberIssueDateSnapshot,
                                    String identificationNumberIssuePlaceSnapshot, String ethnicitySnapshot,
                                    String subjectTypeSnapshot, String payerSourceSnapshot, String bloodGroupSnapshot,
                                    String phoneSnapshot, String provinceSnapshot, String wardSnapshot,
                                    String addressDetailSnapshot, String occupationSnapshot,
                                    String workplaceOrSchoolSnapshot, String healthExaminationReasonSnapshot,
                                    LocalDate plannedExaminationDate, AggregateId masterTemplateVersionId,
                                    AggregateId replacesHealthExaminationRecordId, LocalDate actualExaminationDate,
                                    HealthExaminationRecordStatus status) {
        this.id = id;
        this.shs = shs;
        this.patientId = patientId;
        this.encounterId = encounterId;
        this.batchParticipantId = batchParticipantId;
        this.fullNameSnapshot = fullNameSnapshot;
        this.dateOfBirthSnapshot = dateOfBirthSnapshot;
        this.sexSnapshot = sexSnapshot;
        this.identificationNumberSnapshot = identificationNumberSnapshot;
        this.identificationNumberIssueDateSnapshot = identificationNumberIssueDateSnapshot;
        this.identificationNumberIssuePlaceSnapshot = identificationNumberIssuePlaceSnapshot;
        this.ethnicitySnapshot = ethnicitySnapshot;
        this.subjectTypeSnapshot = subjectTypeSnapshot;
        this.payerSourceSnapshot = payerSourceSnapshot;
        this.bloodGroupSnapshot = bloodGroupSnapshot;
        this.phoneSnapshot = phoneSnapshot;
        this.provinceSnapshot = provinceSnapshot;
        this.wardSnapshot = wardSnapshot;
        this.addressDetailSnapshot = addressDetailSnapshot;
        this.occupationSnapshot = occupationSnapshot;
        this.workplaceOrSchoolSnapshot = workplaceOrSchoolSnapshot;
        this.healthExaminationReasonSnapshot = healthExaminationReasonSnapshot;
        this.plannedExaminationDate = plannedExaminationDate;
        this.masterTemplateVersionId = masterTemplateVersionId;
        this.replacesHealthExaminationRecordId = replacesHealthExaminationRecordId;
        this.actualExaminationDate = actualExaminationDate;
        this.status = status;
    }

    public static HealthExaminationRecord prepare(AggregateId id, ShsCode shs, AggregateId patientId,
                                                  AggregateId encounterId, String fullNameSnapshot,
                                                  LocalDate dateOfBirthSnapshot, String sexSnapshot,
                                                  IdentificationNumber identificationNumberSnapshot,
                                                  LocalDate plannedExaminationDate,
                                                  AggregateId masterTemplateVersionId) {
        return prepare(id, shs, patientId, encounterId, null, fullNameSnapshot, dateOfBirthSnapshot,
                sexSnapshot, identificationNumberSnapshot, null, null, null, null, null, null, null, null,
                null, null, null, null, null, plannedExaminationDate, masterTemplateVersionId);
    }

    public static HealthExaminationRecord prepare(AggregateId id, ShsCode shs, AggregateId patientId,
                                                  AggregateId encounterId, AggregateId batchParticipantId,
                                                  String fullNameSnapshot, LocalDate dateOfBirthSnapshot,
                                                  String sexSnapshot, IdentificationNumber identificationNumberSnapshot,
                                                  LocalDate identificationNumberIssueDateSnapshot,
                                                  String identificationNumberIssuePlaceSnapshot, String ethnicitySnapshot,
                                                  String subjectTypeSnapshot, String payerSourceSnapshot,
                                                  String bloodGroupSnapshot, String phoneSnapshot,
                                                  String provinceSnapshot, String wardSnapshot,
                                                  String addressDetailSnapshot, String occupationSnapshot,
                                                  String workplaceOrSchoolSnapshot,
                                                  String healthExaminationReasonSnapshot,
                                                  LocalDate plannedExaminationDate,
                                                  AggregateId masterTemplateVersionId) {
        return prepareInternal(id, shs, patientId, encounterId, batchParticipantId, fullNameSnapshot,
                dateOfBirthSnapshot, sexSnapshot, identificationNumberSnapshot, identificationNumberIssueDateSnapshot,
                identificationNumberIssuePlaceSnapshot, ethnicitySnapshot, subjectTypeSnapshot, payerSourceSnapshot,
                bloodGroupSnapshot, phoneSnapshot, provinceSnapshot, wardSnapshot, addressDetailSnapshot,
                occupationSnapshot, workplaceOrSchoolSnapshot, healthExaminationReasonSnapshot,
                plannedExaminationDate, masterTemplateVersionId, null);
    }

    public static HealthExaminationRecord prepareReplacement(AggregateId id,
                                                              AggregateId replacesHealthExaminationRecordId,
                                                              ShsCode shs, AggregateId patientId,
                                                              AggregateId encounterId, AggregateId batchParticipantId,
                                                              String fullNameSnapshot, LocalDate dateOfBirthSnapshot,
                                                              String sexSnapshot,
                                                              IdentificationNumber identificationNumberSnapshot,
                                                              LocalDate identificationNumberIssueDateSnapshot,
                                                              String identificationNumberIssuePlaceSnapshot,
                                                              String ethnicitySnapshot, String subjectTypeSnapshot,
                                                              String payerSourceSnapshot, String bloodGroupSnapshot,
                                                              String phoneSnapshot, String provinceSnapshot,
                                                              String wardSnapshot, String addressDetailSnapshot,
                                                              String occupationSnapshot,
                                                              String workplaceOrSchoolSnapshot,
                                                              String healthExaminationReasonSnapshot,
                                                              LocalDate plannedExaminationDate,
                                                              AggregateId masterTemplateVersionId) {
        if (replacesHealthExaminationRecordId == null) {
            throw new IllegalArgumentException("Missing record being replaced");
        }
        return prepareInternal(id, shs, patientId, encounterId, batchParticipantId, fullNameSnapshot,
                dateOfBirthSnapshot, sexSnapshot, identificationNumberSnapshot, identificationNumberIssueDateSnapshot,
                identificationNumberIssuePlaceSnapshot, ethnicitySnapshot, subjectTypeSnapshot, payerSourceSnapshot,
                bloodGroupSnapshot, phoneSnapshot, provinceSnapshot, wardSnapshot, addressDetailSnapshot,
                occupationSnapshot, workplaceOrSchoolSnapshot, healthExaminationReasonSnapshot,
                plannedExaminationDate, masterTemplateVersionId, replacesHealthExaminationRecordId);
    }

    private static HealthExaminationRecord prepareInternal(AggregateId id, ShsCode shs, AggregateId patientId,
                                                           AggregateId encounterId, AggregateId batchParticipantId,
                                                           String fullNameSnapshot, LocalDate dateOfBirthSnapshot,
                                                           String sexSnapshot,
                                                           IdentificationNumber identificationNumberSnapshot,
                                                           LocalDate identificationNumberIssueDateSnapshot,
                                                           String identificationNumberIssuePlaceSnapshot,
                                                           String ethnicitySnapshot, String subjectTypeSnapshot,
                                                           String payerSourceSnapshot, String bloodGroupSnapshot,
                                                           String phoneSnapshot, String provinceSnapshot,
                                                           String wardSnapshot, String addressDetailSnapshot,
                                                           String occupationSnapshot,
                                                           String workplaceOrSchoolSnapshot,
                                                           String healthExaminationReasonSnapshot,
                                                           LocalDate plannedExaminationDate,
                                                           AggregateId masterTemplateVersionId,
                                                           AggregateId replacesHealthExaminationRecordId) {
        validateRequiredFields(id, shs, patientId, encounterId, fullNameSnapshot, dateOfBirthSnapshot, sexSnapshot,
                identificationNumberSnapshot, plannedExaminationDate, masterTemplateVersionId);
        if (id.equals(replacesHealthExaminationRecordId)) {
            throw new IllegalArgumentException("A record cannot replace itself");
        }
        requireAdult(dateOfBirthSnapshot, plannedExaminationDate);
        return new HealthExaminationRecord(id, shs, patientId, encounterId, batchParticipantId, fullNameSnapshot,
                dateOfBirthSnapshot, sexSnapshot, identificationNumberSnapshot, identificationNumberIssueDateSnapshot,
                identificationNumberIssuePlaceSnapshot, ethnicitySnapshot, subjectTypeSnapshot, payerSourceSnapshot,
                bloodGroupSnapshot, phoneSnapshot, provinceSnapshot, wardSnapshot, addressDetailSnapshot,
                occupationSnapshot, workplaceOrSchoolSnapshot, healthExaminationReasonSnapshot,
                plannedExaminationDate, masterTemplateVersionId, replacesHealthExaminationRecordId, null,
                HealthExaminationRecordStatus.ACTIVE);
    }

    public static HealthExaminationRecord restore(AggregateId id, ShsCode shs, AggregateId patientId,
                                                  AggregateId encounterId, AggregateId batchParticipantId,
                                                  String fullNameSnapshot, LocalDate dateOfBirthSnapshot,
                                                  String sexSnapshot, IdentificationNumber identificationNumberSnapshot,
                                                  LocalDate plannedDate, LocalDate actualDate,
                                                  AggregateId masterTemplateVersionId,
                                                  HealthExaminationRecordStatus status) {
        return restore(id, shs, patientId, encounterId, batchParticipantId, fullNameSnapshot, dateOfBirthSnapshot,
                sexSnapshot, identificationNumberSnapshot, null, null, null, null, null, null, null, null, null,
                null, null, null, null, plannedDate, actualDate, masterTemplateVersionId, null, status);
    }

    public static HealthExaminationRecord restore(AggregateId id, ShsCode shs, AggregateId patientId,
                                                  AggregateId encounterId, AggregateId batchParticipantId,
                                                  String fullNameSnapshot, LocalDate dateOfBirthSnapshot,
                                                  String sexSnapshot, IdentificationNumber identificationNumberSnapshot,
                                                  LocalDate identificationNumberIssueDateSnapshot,
                                                  String identificationNumberIssuePlaceSnapshot, String ethnicitySnapshot,
                                                  String subjectTypeSnapshot, String payerSourceSnapshot,
                                                  String bloodGroupSnapshot, String phoneSnapshot,
                                                  String provinceSnapshot, String wardSnapshot,
                                                  String addressDetailSnapshot, String occupationSnapshot,
                                                  String workplaceOrSchoolSnapshot,
                                                  String healthExaminationReasonSnapshot, LocalDate plannedDate,
                                                  LocalDate actualDate, AggregateId masterTemplateVersionId,
                                                  AggregateId replacesHealthExaminationRecordId,
                                                  HealthExaminationRecordStatus status) {
        validateRequiredFields(id, shs, patientId, encounterId, fullNameSnapshot, dateOfBirthSnapshot, sexSnapshot,
                identificationNumberSnapshot, plannedDate, masterTemplateVersionId);
        if (status == null || (status == HealthExaminationRecordStatus.COMPLETED && actualDate == null)
                || id.equals(replacesHealthExaminationRecordId)) {
            throw new IllegalArgumentException("Invalid persisted health-examination record");
        }
        requireAdult(dateOfBirthSnapshot, plannedDate);
        if (actualDate != null) requireAdult(dateOfBirthSnapshot, actualDate);
        return new HealthExaminationRecord(id, shs, patientId, encounterId, batchParticipantId, fullNameSnapshot,
                dateOfBirthSnapshot, sexSnapshot, identificationNumberSnapshot, identificationNumberIssueDateSnapshot,
                identificationNumberIssuePlaceSnapshot, ethnicitySnapshot, subjectTypeSnapshot, payerSourceSnapshot,
                bloodGroupSnapshot, phoneSnapshot, provinceSnapshot, wardSnapshot, addressDetailSnapshot,
                occupationSnapshot, workplaceOrSchoolSnapshot, healthExaminationReasonSnapshot, plannedDate,
                masterTemplateVersionId, replacesHealthExaminationRecordId, actualDate, status);
    }

    private static void validateRequiredFields(AggregateId id, ShsCode shs, AggregateId patientId,
                                               AggregateId encounterId, String fullNameSnapshot,
                                               LocalDate dateOfBirthSnapshot, String sexSnapshot,
                                               IdentificationNumber identificationNumberSnapshot,
                                               LocalDate plannedDate, AggregateId masterTemplateVersionId) {
        if (id == null || shs == null || patientId == null || encounterId == null
                || fullNameSnapshot == null || fullNameSnapshot.isBlank() || dateOfBirthSnapshot == null
                || sexSnapshot == null || sexSnapshot.isBlank() || identificationNumberSnapshot == null
                || plannedDate == null || masterTemplateVersionId == null) {
            throw new IllegalArgumentException("Incomplete health-examination record");
        }
    }

    public void checkIn(LocalDate actualDate) {
        if (actualDate == null) throw new IllegalArgumentException("Missing actual examination date");
        if (status != HealthExaminationRecordStatus.ACTIVE) {
            throw new DomainRuleViolation("Health-examination record is not active");
        }
        requireAdult(dateOfBirthSnapshot, actualDate);
        if (actualExaminationDate != null && !actualExaminationDate.equals(actualDate)) {
            throw new DomainRuleViolation("Already checked in on another date");
        }
        actualExaminationDate = actualDate;
    }

    private static void requireAdult(LocalDate birthDate, LocalDate date) {
        LocalDate eighteenthBirthday = birthDate.plusYears(18);
        if (birthDate.getMonthValue() == 2 && birthDate.getDayOfMonth() == 29
                && !eighteenthBirthday.isLeapYear()) {
            eighteenthBirthday = eighteenthBirthday.plusDays(1);
        }
        if (eighteenthBirthday.isAfter(date)) throw new AdultEligibilityViolation();
    }

    public void complete() {
        if (actualExaminationDate == null) throw new DomainRuleViolation("Record has not been checked in");
        transitionFromActive(HealthExaminationRecordStatus.COMPLETED);
    }

    public void cancel() { transitionFromActive(HealthExaminationRecordStatus.CANCELED); }
    public void markReplaced() { transitionFromActive(HealthExaminationRecordStatus.REPLACED); }

    private void transitionFromActive(HealthExaminationRecordStatus next) {
        if (status != HealthExaminationRecordStatus.ACTIVE) {
            throw new DomainRuleViolation("Invalid health-examination record transition");
        }
        status = next;
    }

    public AggregateId id() { return id; }
    public AggregateId batchParticipantId() { return batchParticipantId; }
    public ShsCode shs() { return shs; }
    public AggregateId patientId() { return patientId; }
    public AggregateId encounterId() { return encounterId; }
    public String fullNameSnapshot() { return fullNameSnapshot; }
    public LocalDate dateOfBirthSnapshot() { return dateOfBirthSnapshot; }
    public String sexSnapshot() { return sexSnapshot; }
    public IdentificationNumber identificationNumberSnapshot() { return identificationNumberSnapshot; }
    public LocalDate identificationNumberIssueDateSnapshot() { return identificationNumberIssueDateSnapshot; }
    public String identificationNumberIssuePlaceSnapshot() { return identificationNumberIssuePlaceSnapshot; }
    public String ethnicitySnapshot() { return ethnicitySnapshot; }
    public String subjectTypeSnapshot() { return subjectTypeSnapshot; }
    public String payerSourceSnapshot() { return payerSourceSnapshot; }
    public String bloodGroupSnapshot() { return bloodGroupSnapshot; }
    public String phoneSnapshot() { return phoneSnapshot; }
    public String provinceSnapshot() { return provinceSnapshot; }
    public String wardSnapshot() { return wardSnapshot; }
    public String addressDetailSnapshot() { return addressDetailSnapshot; }
    public String occupationSnapshot() { return occupationSnapshot; }
    public String workplaceOrSchoolSnapshot() { return workplaceOrSchoolSnapshot; }
    public String healthExaminationReasonSnapshot() { return healthExaminationReasonSnapshot; }
    public LocalDate plannedExaminationDate() { return plannedExaminationDate; }
    public LocalDate actualExaminationDate() { return actualExaminationDate; }
    public AggregateId masterTemplateVersionId() { return masterTemplateVersionId; }
    public AggregateId replacesHealthExaminationRecordId() { return replacesHealthExaminationRecordId; }
    public HealthExaminationRecordStatus status() { return status; }
}
