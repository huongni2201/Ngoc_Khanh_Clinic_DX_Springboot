package com.ngockhanh.clinic.healthexamination.application.query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/**
 * Read contract of one Participant row of the list. The identification number is complete here and
 * is masked when mapped to the public response.
 */
@Builder
public record ParticipantSummary(
    UUID id,
    UUID batchId,
    UUID batchDayId,
    LocalDate examinationDate,
    String participantCode,
    String fullName,
    LocalDate dateOfBirth,
    String sex,
    String identificationNumber,
    String departmentName,
    String positionName,
    String rosterStatus,
    String attendanceStatus,
    String reconciliationStatus,
    LocalDate actualExaminationDate,
    Instant preparedAt,
    long rowVersion) {}
