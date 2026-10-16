package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationParticipant;
import com.ngockhanh.clinic.healthexamination.domain.enums.AssignmentStatus;
import com.ngockhanh.clinic.healthexamination.domain.enums.AttendanceStatus;
import com.ngockhanh.clinic.healthexamination.domain.repository.SessionAssignmentRepository.ParticipantAssignment;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * One Participant in full, for the edit form. Unlike the list it carries the complete
 * identification number, phone and email, so it is only returned to callers that may manage
 * Participants. It never carries the Patient identifier or import history. The schedule is the list
 * of the Participant's session assignments, newest session first.
 *
 * @param patientLinked whether the Participant was prepared for a visit; the identification number
 *     is then locked
 * @param source {@code IMPORT} for an Excel import, {@code MANUAL} for a Participant added by hand
 * @param attendanceStatus attendance derived from the assignments
 * @param assignments every assignment of the Participant, history included
 */
@Builder
public record ParticipantDetailResponse(
    UUID id,
    UUID agreementId,
    String participantCode,
    String fullName,
    LocalDate dateOfBirth,
    String sex,
    String identificationNumberMasked,
    String identificationNumber,
    LocalDate identificationIssueDate,
    String identificationIssuePlace,
    String ethnicity,
    String phone,
    String email,
    String address,
    String workplace,
    String departmentName,
    String positionName,
    String note,
    String rosterStatus,
    String attendanceStatus,
    String reconciliationStatus,
    Instant preparedAt,
    long rowVersion,
    boolean patientLinked,
    String source,
    List<ParticipantAssignmentResponse> assignments,
    Instant createdAt,
    Instant updatedAt) {
  public ParticipantDetailResponse {
    assignments = List.copyOf(assignments);
  }

  public static ParticipantDetailResponse from(
      HealthExaminationParticipant participant, List<ParticipantAssignment> assignments) {
    var roster = participant.getRoster();
    String identification = roster.identificationNumber().value();
    return ParticipantDetailResponse.builder()
        .id(participant.getId().value())
        .agreementId(participant.getAgreementId().value())
        .participantCode(roster.participantCode())
        .fullName(roster.fullName())
        .dateOfBirth(roster.dateOfBirth())
        .sex(roster.sex())
        .identificationNumberMasked(ParticipantSummaryResponse.mask(identification))
        .identificationNumber(identification)
        .identificationIssueDate(roster.identificationIssueDate())
        .identificationIssuePlace(roster.identificationIssuePlace())
        .ethnicity(roster.ethnicity())
        .phone(roster.phone())
        .email(roster.email())
        .address(roster.address())
        .workplace(roster.workplace())
        .departmentName(roster.departmentName())
        .positionName(roster.positionName())
        .note(roster.note())
        .rosterStatus(participant.getRosterStatus().name())
        .attendanceStatus(attendance(assignments).name())
        .reconciliationStatus(participant.getReconciliationStatus().name())
        .preparedAt(participant.getPreparedAt())
        .rowVersion(participant.getRowVersion())
        .patientLinked(participant.getPatientId() != null)
        .source(participant.isManual() ? "MANUAL" : "IMPORT")
        .assignments(assignments.stream().map(ParticipantAssignmentResponse::from).toList())
        .createdAt(participant.getCreatedAt())
        .updatedAt(participant.getUpdatedAt())
        .build();
  }

  /**
   * Same rule as the SQL read model: attended wins; no-show only when nothing is still expected.
   */
  static AttendanceStatus attendance(List<ParticipantAssignment> assignments) {
    boolean attended = false;
    boolean expected = false;
    boolean noShow = false;
    for (var assignment : assignments) {
      if (assignment.status() == AssignmentStatus.ATTENDED) attended = true;
      if (assignment.status() == AssignmentStatus.ASSIGNED) expected = true;
      if (assignment.status() == AssignmentStatus.NO_SHOW) noShow = true;
    }
    if (attended) return AttendanceStatus.ATTENDED;
    return noShow && !expected ? AttendanceStatus.NO_SHOW : AttendanceStatus.UNCONFIRMED;
  }
}
