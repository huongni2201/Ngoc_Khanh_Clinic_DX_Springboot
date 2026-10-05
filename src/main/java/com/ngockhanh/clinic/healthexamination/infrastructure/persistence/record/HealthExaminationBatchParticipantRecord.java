package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.record;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/** A row in public.health_examination_batch_participants. */
@Builder
public record HealthExaminationBatchParticipantRecord(
    UUID id,
    UUID batchId,
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
    UUID attendanceRecordedBy,
    Instant attendanceRecordedAt,
    String attendanceNote,
    String serviceReconciliationStatus,
    UUID servicesReconciledBy,
    Instant servicesReconciledAt,
    UUID importJobId,
    Integer sourceRowNumber,
    Instant preparedAt,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {}
