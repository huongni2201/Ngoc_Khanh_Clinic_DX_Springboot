package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * SQL projection of one Participant list row with what its session assignments say: the derived
 * attendance, the number of sessions the Participant is in, the next expected session date and the
 * last attended session date.
 */
public record ParticipantSummaryView(
    UUID id,
    UUID agreementId,
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
    long liveAssignmentCount,
    LocalDate nextSessionDate,
    LocalDate lastAttendedDate,
    Instant preparedAt,
    long rowVersion) {}
