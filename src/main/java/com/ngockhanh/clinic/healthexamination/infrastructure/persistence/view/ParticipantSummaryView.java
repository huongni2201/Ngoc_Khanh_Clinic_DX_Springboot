package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** SQL projection of one Participant list row joined with its examination day. */
public record ParticipantSummaryView(
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
