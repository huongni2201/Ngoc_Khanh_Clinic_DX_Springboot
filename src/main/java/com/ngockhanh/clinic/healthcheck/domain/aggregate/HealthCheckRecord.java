package com.ngockhanh.clinic.healthcheck.domain.aggregate;

import java.util.UUID;

import com.ngockhanh.clinic.healthcheck.domain.exception.DomainRuleViolation;

import com.ngockhanh.clinic.healthcheck.domain.exception.AdultEligibilityViolation;
import com.ngockhanh.clinic.healthcheck.domain.enums.HealthCheckRecordStatus;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.AdministrativeSnapshot;
import com.ngockhanh.clinic.healthcheck.domain.valueobject.ShsCode;
import java.time.LocalDate;

public final class HealthCheckRecord {
    private final UUID id;
    private final UUID batchEmployeeId;
    private final ShsCode shs;
    private final UUID patientId;
    private final UUID encounterId;
    private final AdministrativeSnapshot snapshot;
    private final LocalDate plannedExaminationDate;
    private final UUID masterTemplateVersionId;
    private LocalDate actualExaminationDate;
    private HealthCheckRecordStatus status = HealthCheckRecordStatus.ACTIVE;

    private HealthCheckRecord(UUID id, ShsCode shs, UUID patientId, UUID encounterId, UUID batchEmployeeId, AdministrativeSnapshot snapshot,
                              LocalDate plannedExaminationDate, UUID masterTemplateVersionId) {
        this.id = id;
        this.batchEmployeeId = batchEmployeeId;
        this.shs = shs;
        this.patientId = patientId;
        this.encounterId = encounterId;
        this.snapshot = snapshot;
        this.plannedExaminationDate = plannedExaminationDate;
        this.masterTemplateVersionId = masterTemplateVersionId;
    }

    public static HealthCheckRecord prepare(ShsCode shs, UUID patientId, UUID encounterId, AdministrativeSnapshot snapshot,
                                            LocalDate plannedExaminationDate, UUID masterTemplateVersionId) {
        return prepare(null, shs, patientId, encounterId, null, snapshot, plannedExaminationDate, masterTemplateVersionId);
    }

    public static HealthCheckRecord prepare(UUID id, ShsCode shs, UUID patientId, UUID encounterId, UUID batchEmployeeId,
                                            AdministrativeSnapshot snapshot, LocalDate plannedExaminationDate, UUID masterTemplateVersionId) {
        if (id == null || shs == null || patientId == null || encounterId == null || (batchEmployeeId != null && batchEmployeeId == null)
                || snapshot == null || plannedExaminationDate == null || masterTemplateVersionId == null) {
            throw new IllegalArgumentException("Incomplete health-check record");
        }
        requireAdult(snapshot.dateOfBirth(), plannedExaminationDate);
        return new HealthCheckRecord(id, shs, patientId, encounterId, batchEmployeeId, snapshot, plannedExaminationDate, masterTemplateVersionId);
    }

    public static HealthCheckRecord restore(UUID id, ShsCode shs, UUID patientId, UUID encounterId, UUID batchEmployeeId,
                                            AdministrativeSnapshot snapshot, LocalDate plannedDate, LocalDate actualDate,
                                            UUID masterTemplateVersionId, HealthCheckRecordStatus status) {
        if (id == null || status == null || (status == HealthCheckRecordStatus.COMPLETED && actualDate == null)) {
            throw new IllegalArgumentException("Invalid persisted health-check record");
        }
        HealthCheckRecord record = prepare(id, shs, patientId, encounterId, batchEmployeeId, snapshot, plannedDate, masterTemplateVersionId);
        if (actualDate != null) {
            requireAdult(snapshot.dateOfBirth(), actualDate);
            record.actualExaminationDate = actualDate;
        }
        record.status = status;
        return record;
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

    public ShsCode shs() { return shs; }
    public HealthCheckRecordStatus status() { return status; }
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
    public AdministrativeSnapshot snapshot() { return snapshot; }
    public LocalDate actualExaminationDate() { return actualExaminationDate; }
}

