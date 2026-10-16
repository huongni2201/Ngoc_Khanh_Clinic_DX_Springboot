package com.ngockhanh.clinic.healthexamination.infrastructure.persistence.view;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** SQL projection of one assignment of a Participant joined with its session. */
public record ParticipantAssignmentView(
    UUID assignmentId,
    UUID sessionId,
    LocalDate sessionDate,
    String activity,
    String siteType,
    String siteAddress,
    String sessionStatus,
    String status,
    UUID replacedByAssignmentId,
    Instant attendedAt,
    String note,
    long rowVersion) {}
