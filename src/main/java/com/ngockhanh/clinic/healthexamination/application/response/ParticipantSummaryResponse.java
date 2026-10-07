package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.healthexamination.application.query.ParticipantSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/**
 * One Participant of the list. It deliberately omits the complete identification number, phone,
 * email, import history and clinical content; the identification number is masked.
 */
@Builder
public record ParticipantSummaryResponse(
    UUID id,
    UUID batchId,
    UUID batchDayId,
    LocalDate examinationDate,
    String participantCode,
    String fullName,
    LocalDate dateOfBirth,
    String sex,
    String identificationNumberMasked,
    String departmentName,
    String positionName,
    String rosterStatus,
    String attendanceStatus,
    String reconciliationStatus,
    LocalDate actualExaminationDate,
    Instant preparedAt,
    long rowVersion) {
  private static final int VISIBLE_TAIL = 4;

  public static ParticipantSummaryResponse from(ParticipantSummary row) {
    return ParticipantSummaryResponse.builder()
        .id(row.id())
        .batchId(row.batchId())
        .batchDayId(row.batchDayId())
        .examinationDate(row.examinationDate())
        .participantCode(row.participantCode())
        .fullName(row.fullName())
        .dateOfBirth(row.dateOfBirth())
        .sex(row.sex())
        .identificationNumberMasked(mask(row.identificationNumber()))
        .departmentName(row.departmentName())
        .positionName(row.positionName())
        .rosterStatus(row.rosterStatus())
        .attendanceStatus(row.attendanceStatus())
        .reconciliationStatus(row.reconciliationStatus())
        .actualExaminationDate(row.actualExaminationDate())
        .preparedAt(row.preparedAt())
        .rowVersion(row.rowVersion())
        .build();
  }

  /** Replaces every character but the last four with {@code *}; short values are fully masked. */
  static String mask(String identificationNumber) {
    int length = identificationNumber.length();
    int visible = length <= VISIBLE_TAIL ? 0 : VISIBLE_TAIL;
    return "*".repeat(length - visible) + identificationNumber.substring(length - visible);
  }
}
