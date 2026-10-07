package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailRow;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * One Participant row of the examination detail matrix. It omits the complete identification
 * number, phone, email and attendance note; the identification number is masked.
 *
 * @param performedBatchServiceIds batch services recorded as performed; the columns of the matrix
 *     are the batch services of the batch detail
 */
@Builder
public record ExaminationDetailRowResponse(
    UUID id,
    String participantCode,
    String fullName,
    String identificationNumberMasked,
    String departmentName,
    String positionName,
    LocalDate examinationDate,
    String attendanceStatus,
    LocalDate actualExaminationDate,
    String reconciliationStatus,
    List<UUID> performedBatchServiceIds,
    long rowVersion) {
  public static ExaminationDetailRowResponse from(ExaminationDetailRow row) {
    return ExaminationDetailRowResponse.builder()
        .id(row.id())
        .participantCode(row.participantCode())
        .fullName(row.fullName())
        .identificationNumberMasked(ParticipantSummaryResponse.mask(row.identificationNumber()))
        .departmentName(row.departmentName())
        .positionName(row.positionName())
        .examinationDate(row.examinationDate())
        .attendanceStatus(row.attendanceStatus())
        .actualExaminationDate(row.actualExaminationDate())
        .reconciliationStatus(row.reconciliationStatus())
        .performedBatchServiceIds(row.performedBatchServiceIds())
        .rowVersion(row.rowVersion())
        .build();
  }
}
