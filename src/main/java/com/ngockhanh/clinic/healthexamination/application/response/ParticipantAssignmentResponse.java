package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.healthexamination.domain.repository.SessionAssignmentRepository.ParticipantAssignment;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/**
 * One assignment of a Participant with the session it is for: the Participant's schedule history. A
 * moved or cancelled assignment stays in the list as history.
 *
 * @param replacedByAssignmentId the assignment that replaced this one when it was moved, else null
 */
@Builder
public record ParticipantAssignmentResponse(
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
    long rowVersion) {
  public static ParticipantAssignmentResponse from(ParticipantAssignment a) {
    return ParticipantAssignmentResponse.builder()
        .assignmentId(a.assignmentId())
        .sessionId(a.sessionId())
        .sessionDate(a.sessionDate())
        .activity(a.activity().name())
        .siteType(a.siteType().name())
        .siteAddress(a.siteAddress())
        .sessionStatus(a.sessionStatus().name())
        .status(a.status().name())
        .replacedByAssignmentId(a.replacedByAssignmentId())
        .attendedAt(a.attendedAt())
        .note(a.note())
        .rowVersion(a.rowVersion())
        .build();
  }
}
