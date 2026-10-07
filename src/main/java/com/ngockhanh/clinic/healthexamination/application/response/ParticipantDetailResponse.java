package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationBatchParticipant;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/**
 * One Participant in full, for the edit form. Unlike the list it carries the complete
 * identification number, phone and email, so it is only returned to callers that may manage
 * Participants. It never carries the Patient identifier, the attendance note or import history.
 *
 * @param patientLinked whether the Participant was prepared for a visit; the identification number
 *     is then locked
 * @param source {@code IMPORT} for an Excel import, {@code MANUAL} for a Participant added by hand
 */
@Builder
public record ParticipantDetailResponse(
    UUID id,
    UUID batchId,
    UUID batchDayId,
    LocalDate examinationDate,
    String participantCode,
    String fullName,
    LocalDate dateOfBirth,
    String sex,
    String identificationNumberMasked,
    String identificationNumber,
    String phone,
    String email,
    String departmentName,
    String positionName,
    String rosterStatus,
    String attendanceStatus,
    String reconciliationStatus,
    LocalDate actualExaminationDate,
    Instant preparedAt,
    long rowVersion,
    boolean patientLinked,
    String source,
    Instant createdAt,
    Instant updatedAt) {
  public static ParticipantDetailResponse from(
      HealthExaminationBatchParticipant participant, LocalDate examinationDate) {
    var roster = participant.getRoster();
    String identification = roster.identificationNumber().value();
    return ParticipantDetailResponse.builder()
        .id(participant.getId().value())
        .batchId(participant.getBatchId().value())
        .batchDayId(participant.getBatchDayId().value())
        .examinationDate(examinationDate)
        .participantCode(roster.participantCode())
        .fullName(roster.fullName())
        .dateOfBirth(roster.dateOfBirth())
        .sex(roster.sex())
        .identificationNumberMasked(ParticipantSummaryResponse.mask(identification))
        .identificationNumber(identification)
        .phone(roster.phone())
        .email(roster.email())
        .departmentName(roster.departmentName())
        .positionName(roster.positionName())
        .rosterStatus(participant.getRosterStatus().name())
        .attendanceStatus(participant.getAttendanceStatus().name())
        .reconciliationStatus(participant.getReconciliationStatus().name())
        .actualExaminationDate(participant.getActualExaminationDate())
        .preparedAt(participant.getPreparedAt())
        .rowVersion(participant.getRowVersion())
        .patientLinked(participant.getPatientId() != null)
        .source(participant.isManual() ? "MANUAL" : "IMPORT")
        .createdAt(participant.getCreatedAt())
        .updatedAt(participant.getUpdatedAt())
        .build();
  }
}
