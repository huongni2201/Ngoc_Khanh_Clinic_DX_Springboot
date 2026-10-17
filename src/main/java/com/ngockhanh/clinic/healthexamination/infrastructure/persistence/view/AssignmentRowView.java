package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** SQL projection of one assignment of a session joined with its Participant. */
public record AssignmentRowView(
    UUID assignmentId,
    String status,
    Instant attendedAt,
    String note,
    long assignmentRowVersion,
    UUID participantId,
    UUID agreementId,
    String participantCode,
    String fullName,
    LocalDate dateOfBirth,
    String sex,
    String identificationNumber,
    String departmentName,
    String positionName,
    String rosterStatus) {}
