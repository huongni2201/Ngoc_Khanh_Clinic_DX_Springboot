package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.SessionAssignment;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/**
 * One assignment as stored after a change.
 *
 * @param replacedByAssignmentId the assignment that replaced this one when it was moved, else null
 */
@Builder
public record AssignmentResponse(
    UUID id,
    UUID participantId,
    UUID sessionId,
    String status,
    UUID replacedByAssignmentId,
    Instant attendedAt,
    String note,
    long rowVersion) {
  public static AssignmentResponse from(SessionAssignment a, long rowVersion) {
    return AssignmentResponse.builder()
        .id(a.id().value())
        .participantId(a.participantId().value())
        .sessionId(a.sessionId().value())
        .status(a.status().name())
        .replacedByAssignmentId(
            a.replacedByAssignmentId() == null ? null : a.replacedByAssignmentId().value())
        .attendedAt(a.attendedAt())
        .note(a.note())
        .rowVersion(rowVersion)
        .build();
  }
}
