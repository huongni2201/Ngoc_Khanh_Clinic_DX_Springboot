package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.healthexamination.domain.repository.SessionAssignmentRepository.AssignmentRow;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/**
 * One Participant of a session with the assignment. The identification number is masked; phone and
 * email are not included.
 */
@Builder
public record AssignmentRowResponse(
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
    String identificationNumberMasked,
    String departmentName,
    String positionName,
    String rosterStatus) {
  public static AssignmentRowResponse from(AssignmentRow row) {
    return AssignmentRowResponse.builder()
        .assignmentId(row.assignmentId())
        .status(row.status().name())
        .attendedAt(row.attendedAt())
        .note(row.note())
        .assignmentRowVersion(row.assignmentRowVersion())
        .participantId(row.participantId())
        .agreementId(row.agreementId())
        .participantCode(row.participantCode())
        .fullName(row.fullName())
        .dateOfBirth(row.dateOfBirth())
        .sex(row.sex())
        .identificationNumberMasked(ParticipantSummaryResponse.mask(row.identificationNumber()))
        .departmentName(row.departmentName())
        .positionName(row.positionName())
        .rosterStatus(row.rosterStatus().name())
        .build();
  }
}
