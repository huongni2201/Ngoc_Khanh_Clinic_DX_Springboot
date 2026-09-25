package com.ngockhanh.clinic.healthcheck.domain.aggregate;

import java.time.LocalDate;
import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.enums.HealthExaminationRecordStatus;
import com.ngockhanh.clinic.healthcheck.domain.exception.AdultEligibilityViolation;
import com.ngockhanh.clinic.healthcheck.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.AdministrativeSnapshot;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.ShsCode;

public final class HealthExaminationRecord {
    private final UUID id;
    private final UUID batchEmployeeId;
    private final ShsCode shs;
    private final UUID patientId;
    private final UUID encounterId;
    private final AdministrativeSnapshot snapshot;
    private final LocalDate plannedExaminationDate;
    private final UUID masterTemplateVersionId;
    private final UUID replacesHealthExaminationRecordId;
    private LocalDate actualExaminationDate;
    private HealthExaminationRecordStatus status;

    private HealthExaminationRecord(UUID id, ShsCode shs, UUID patientId, UUID encounterId, UUID batchEmployeeId,
                              AdministrativeSnapshot snapshot, LocalDate plannedExaminationDate,
                              UUID masterTemplateVersionId, UUID replacesHealthExaminationRecordId,
                              LocalDate actualExaminationDate, HealthExaminationRecordStatus status) {
        this.id = id;
        this.shs = shs;
        this.patientId = patientId;
        this.encounterId = encounterId;
        this.batchEmployeeId = batchEmployeeId;
        this.snapshot = snapshot;
        this.plannedExaminationDate = plannedExaminationDate;
        this.masterTemplateVersionId = masterTemplateVersionId;
        this.replacesHealthExaminationRecordId = replacesHealthExaminationRecordId;
        this.actualExaminationDate = actualExaminationDate;
        this.status = status;
    }

    public static HealthExaminationRecord prepare(UUID id, ShsCode shs, UUID patientId, UUID encounterId,
                                            AdministrativeSnapshot snapshot, LocalDate plannedExaminationDate,
                                            UUID masterTemplateVersionId) {
        return prepare(id, shs, patientId, encounterId, null, snapshot, plannedExaminationDate, masterTemplateVersionId);
    }

    public static HealthExaminationRecord prepare(UUID id, ShsCode shs, UUID patientId, UUID encounterId,
                                            UUID batchEmployeeId, AdministrativeSnapshot snapshot,
                                            LocalDate plannedExaminationDate, UUID masterTemplateVersionId) {
        return prepareInternal(id, shs, patientId, encounterId, batchEmployeeId, snapshot,
                plannedExaminationDate, masterTemplateVersionId, null);
    }

    public static HealthExaminationRecord prepareReplacement(UUID id, UUID replacesHealthExaminationRecordId, ShsCode shs,
                                                       UUID patientId, UUID encounterId, UUID batchEmployeeId,
                                                       AdministrativeSnapshot snapshot, LocalDate plannedExaminationDate,
                                                       UUID masterTemplateVersionId) {
        if (replacesHealthExaminationRecordId == null) throw new IllegalArgumentException("Missing record being replaced");
        return prepareInternal(id, shs, patientId, encounterId, batchEmployeeId, snapshot,
                plannedExaminationDate, masterTemplateVersionId, replacesHealthExaminationRecordId);
    }

    private static HealthExaminationRecord prepareInternal(UUID id, ShsCode shs, UUID patientId, UUID encounterId,
                                                     UUID batchEmployeeId, AdministrativeSnapshot snapshot,
                                                     LocalDate plannedExaminationDate, UUID masterTemplateVersionId,
                                                     UUID replacesHealthExaminationRecordId) {
        validateRequiredFields(id, shs, patientId, encounterId, snapshot, plannedExaminationDate, masterTemplateVersionId);
        if (id.equals(replacesHealthExaminationRecordId)) throw new IllegalArgumentException("A record cannot replace itself");
        requireAdult(snapshot.dateOfBirth(), plannedExaminationDate);
        return new HealthExaminationRecord(id, shs, patientId, encounterId, batchEmployeeId, snapshot,
                plannedExaminationDate, masterTemplateVersionId, replacesHealthExaminationRecordId,
                null, HealthExaminationRecordStatus.ACTIVE);
    }

    public static HealthExaminationRecord restore(UUID id, ShsCode shs, UUID patientId, UUID encounterId,
                                            UUID batchEmployeeId, AdministrativeSnapshot snapshot,
                                            LocalDate plannedDate, LocalDate actualDate,
                                            UUID masterTemplateVersionId, HealthExaminationRecordStatus status) {
        return restore(id, shs, patientId, encounterId, batchEmployeeId, snapshot, plannedDate,
                actualDate, masterTemplateVersionId, null, status);
    }

    public static HealthExaminationRecord restore(UUID id, ShsCode shs, UUID patientId, UUID encounterId,
                                            UUID batchEmployeeId, AdministrativeSnapshot snapshot,
                                            LocalDate plannedDate, LocalDate actualDate,
                                            UUID masterTemplateVersionId, UUID replacesHealthExaminationRecordId,
                                            HealthExaminationRecordStatus status) {
        validateRequiredFields(id, shs, patientId, encounterId, snapshot, plannedDate, masterTemplateVersionId);
        if (status == null || (status == HealthExaminationRecordStatus.COMPLETED && actualDate == null)
                || id.equals(replacesHealthExaminationRecordId)) {
            throw new IllegalArgumentException("Invalid persisted health-examination record");
        }
        requireAdult(snapshot.dateOfBirth(), plannedDate);
        if (actualDate != null) requireAdult(snapshot.dateOfBirth(), actualDate);
        return new HealthExaminationRecord(id, shs, patientId, encounterId, batchEmployeeId, snapshot, plannedDate,
                masterTemplateVersionId, replacesHealthExaminationRecordId, actualDate, status);
    }

    private static void validateRequiredFields(UUID id, ShsCode shs, UUID patientId, UUID encounterId,
                                               AdministrativeSnapshot snapshot, LocalDate plannedDate,
                                               UUID masterTemplateVersionId) {
        if (id == null || shs == null || patientId == null || encounterId == null || snapshot == null
                || plannedDate == null || masterTemplateVersionId == null) {
            throw new IllegalArgumentException("Incomplete health-examination record");
        }
    }

    public void checkIn(LocalDate actualDate) {
        if (actualDate == null) throw new IllegalArgumentException("Missing actual examination date");
        if (status != HealthExaminationRecordStatus.ACTIVE) throw new DomainRuleViolation("Health-examination record is not active");
        requireAdult(snapshot.dateOfBirth(), actualDate);
        if (actualExaminationDate != null && !actualExaminationDate.equals(actualDate)) {
            throw new DomainRuleViolation("Already checked in on another date");
        }
        actualExaminationDate = actualDate;
    }

    private static void requireAdult(LocalDate birthDate, LocalDate date) {
        LocalDate eighteenthBirthday = birthDate.plusYears(18);
        if (birthDate.getMonthValue() == 2 && birthDate.getDayOfMonth() == 29 && !eighteenthBirthday.isLeapYear()) {
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
        if (status != HealthExaminationRecordStatus.ACTIVE) throw new DomainRuleViolation("Invalid health-examination record transition");
        status = next;
    }

    public UUID id() { return id; }
    public UUID batchEmployeeId() { return batchEmployeeId; }
    public ShsCode shs() { return shs; }
    public UUID patientId() { return patientId; }
    public UUID encounterId() { return encounterId; }
    public AdministrativeSnapshot snapshot() { return snapshot; }
    public LocalDate plannedExaminationDate() { return plannedExaminationDate; }
    public LocalDate actualExaminationDate() { return actualExaminationDate; }
    public UUID masterTemplateVersionId() { return masterTemplateVersionId; }
    public UUID replacesHealthExaminationRecordId() { return replacesHealthExaminationRecordId; }
    public HealthExaminationRecordStatus status() { return status; }
}
