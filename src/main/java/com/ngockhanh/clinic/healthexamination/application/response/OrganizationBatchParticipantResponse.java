package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record OrganizationBatchParticipantResponse(
    UUID id,
    UUID batchDayId,
    String participantCode,
    String fullName,
    LocalDate dateOfBirth,
    String sex,
    String identificationNumber,
    String phone,
    String email,
    String departmentName,
    String positionName,
    UUID patientId,
    String rosterStatus,
    String attendanceStatus,
    LocalDate actualExaminationDate,
    String serviceReconciliationStatus,
    Instant preparedAt,
    Instant createdAt,
    long rowVersion) {
  public static OrganizationBatchParticipantResponse from(HealthExaminationBatchParticipant p) {
    var r = p.roster();
    return new OrganizationBatchParticipantResponse(
        p.id().value(),
        p.batchDayId().value(),
        r.participantCode(),
        r.fullName(),
        r.dateOfBirth(),
        r.sex(),
        r.identificationNumber().value(),
        r.phone(),
        r.email(),
        r.departmentName(),
        r.positionName(),
        p.patientId() == null ? null : p.patientId().value(),
        p.rosterStatus().name(),
        p.attendanceStatus().name(),
        p.actualExaminationDate(),
        p.reconciliationStatus().name(),
        p.preparedAt(),
        p.createdAt(),
        p.rowVersion());
  }
}
