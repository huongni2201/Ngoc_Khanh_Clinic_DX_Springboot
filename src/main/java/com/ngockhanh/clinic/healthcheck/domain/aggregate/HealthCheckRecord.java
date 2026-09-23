package com.ngockhanh.clinic.healthcheck.domain.aggregate;

import java.time.LocalDate;
import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.enums.HealthCheckRecordStatus;
import com.ngockhanh.clinic.healthcheck.domain.exception.AdultEligibilityViolation;
import com.ngockhanh.clinic.healthcheck.domain.exception.DomainRuleViolation;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.AdministrativeSnapshot;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.ShsCode;

public final class HealthCheckRecord {
    private final UUID id;
    private final UUID batchEmployeeId;
    private final ShsCode shs;
    private final UUID patientId;
    private final UUID encounterId;
    private final AdministrativeSnapshot snapshot;
    private final LocalDate plannedExaminationDate;
    private final UUID masterTemplateVersionId;
    private final UUID replacesHealthCheckRecordId;
    private LocalDate actualExaminationDate;
    private HealthCheckRecordStatus status;

    private HealthCheckRecord(UUID id, ShsCode shs, UUID patientId, UUID encounterId, UUID batchEmployeeId,
                              AdministrativeSnapshot snapshot, LocalDate plannedExaminationDate,
                              UUID masterTemplateVersionId, UUID replacesHealthCheckRecordId,
                              LocalDate actualExaminationDate, HealthCheckRecordStatus status) {
        this.id = id;
        this.shs = shs;
        this.patientId = patientId;
        this.encounterId = encounterId;
        this.batchEmployeeId = batchEmployeeId;
        this.snapshot = snapshot;
        this.plannedExaminationDate = plannedExaminationDate;
        this.masterTemplateVersionId = masterTemplateVersionId;
        this.replacesHealthCheckRecordId = replacesHealthCheckRecordId;
        this.actualExaminationDate = actualExaminationDate;
        this.status = status;
    }

    public static HealthCheckRecord prepare(UUID id, ShsCode shs, UUID patientId, UUID encounterId,
                                            AdministrativeSnapshot snapshot, LocalDate plannedExaminationDate,
                                            UUID masterTemplateVersionId) {
        return prepare(id, shs, patientId, encounterId, null, snapshot, plannedExaminationDate, masterTemplateVersionId);
    }

    public static HealthCheckRecord prepare(UUID id, ShsCode shs, UUID patientId, UUID encounterId,
                                            UUID batchEmployeeId, AdministrativeSnapshot snapshot,
                                            LocalDate plannedExaminationDate, UUID masterTemplateVersionId) {
        return prepareInternal(id, shs, patientId, encounterId, batchEmployeeId, snapshot,
                plannedExaminationDate, masterTemplateVersionId, null);
    }

    public static HealthCheckRecord prepareReplacement(UUID id, UUID replacesHealthCheckRecordId, ShsCode shs,
                                                       UUID patientId, UUID encounterId, UUID batchEmployeeId,
                                                       AdministrativeSnapshot snapshot, LocalDate plannedExaminationDate,
                                                       UUID masterTemplateVersionId) {
        if (replacesHealthCheckRecordId == null) throw new IllegalArgumentException("Missing record being replaced");
        return prepareInternal(id, shs, patientId, encounterId, batchEmployeeId, snapshot,
                plannedExaminationDate, masterTemplateVersionId, replacesHealthCheckRecordId);
    }

    private static HealthCheckRecord prepareInternal(UUID id, ShsCode shs, UUID patientId, UUID encounterId,
                                                     UUID batchEmployeeId, AdministrativeSnapshot snapshot,
                                                     LocalDate plannedExaminationDate, UUID masterTemplateVersionId,
                                                     UUID replacesHealthCheckRecordId) {
        validateRequiredFields(id, shs, patientId, encounterId, snapshot, plannedExaminationDate, masterTemplateVersionId);
        if (id.equals(replacesHealthCheckRecordId)) throw new IllegalArgumentException("A record cannot replace itself");
        requireAdult(snapshot.dateOfBirth(), plannedExaminationDate);
        return new HealthCheckRecord(id, shs, patientId, encounterId, batchEmployeeId, snapshot,
                plannedExaminationDate, masterTemplateVersionId, replacesHealthCheckRecordId,
                null, HealthCheckRecordStatus.ACTIVE);
    }

    public static HealthCheckRecord restore(UUID id, ShsCode shs, UUID patientId, UUID encounterId,
                                            UUID batchEmployeeId, AdministrativeSnapshot snapshot,
                                            LocalDate plannedDate, LocalDate actualDate,
                                            UUID masterTemplateVersionId, HealthCheckRecordStatus status) {
        return restore(id, shs, patientId, encounterId, batchEmployeeId, snapshot, plannedDate,
                actualDate, masterTemplateVersionId, null, status);
    }

    public static HealthCheckRecord restore(UUID id, ShsCode shs, UUID patientId, UUID encounterId,
                                            UUID batchEmployeeId, AdministrativeSnapshot snapshot,
                                            LocalDate plannedDate, LocalDate actualDate,
                                            UUID masterTemplateVersionId, UUID replacesHealthCheckRecordId,
                                            HealthCheckRecordStatus status) {
        validateRequiredFields(id, shs, patientId, encounterId, snapshot, plannedDate, masterTemplateVersionId);
        if (status == null || (status == HealthCheckRecordStatus.COMPLETED && actualDate == null)
                || id.equals(replacesHealthCheckRecordId)) {
            throw new IllegalArgumentException("Invalid persisted health-check record");
        }
        requireAdult(snapshot.dateOfBirth(), plannedDate);
        if (actualDate != null) requireAdult(snapshot.dateOfBirth(), actualDate);
        return new HealthCheckRecord(id, shs, patientId, encounterId, batchEmployeeId, snapshot, plannedDate,
                masterTemplateVersionId, replacesHealthCheckRecordId, actualDate, status);
    }

    private static void validateRequiredFields(UUID id, ShsCode shs, UUID patientId, UUID encounterId,
                                               AdministrativeSnapshot snapshot, LocalDate plannedDate,
                                               UUID masterTemplateVersionId) {
        if (id == null || shs == null || patientId == null || encounterId == null || snapshot == null
                || plannedDate == null || masterTemplateVersionId == null) {
            throw new IllegalArgumentException("Incomplete health-check record");
        }
    }

    public void checkIn(LocalDate actualDate) {
        if (actualDate == null) throw new IllegalArgumentException("Missing actual examination date");
        if (status != HealthCheckRecordStatus.ACTIVE) throw new DomainRuleViolation("Health-check record is not active");
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
        transitionFromActive(HealthCheckRecordStatus.COMPLETED);
    }

    public void cancel() { transitionFromActive(HealthCheckRecordStatus.CANCELED); }
    public void markReplaced() { transitionFromActive(HealthCheckRecordStatus.REPLACED); }

    private void transitionFromActive(HealthCheckRecordStatus next) {
        if (status != HealthCheckRecordStatus.ACTIVE) throw new DomainRuleViolation("Invalid health-check record transition");
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
    public UUID replacesHealthCheckRecordId() { return replacesHealthCheckRecordId; }
    public HealthCheckRecordStatus status() { return status; }
}
