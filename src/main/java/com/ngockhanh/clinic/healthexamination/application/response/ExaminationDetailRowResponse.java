package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailRow;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * One Participant row of the examination detail matrix. It omits the complete identification
 * number, phone and email; the identification number is masked.
 *
 * @param performedAgreementServiceIds agreement services recorded as performed; the columns of the
 *     matrix are the agreement services of the agreement detail
 */
@Builder
public record ExaminationDetailRowResponse(
    UUID id,
    String participantCode,
    String fullName,
    String identificationNumberMasked,
    String departmentName,
    String positionName,
    LocalDate nextSessionDate,
    String attendanceStatus,
    LocalDate lastAttendedDate,
    String reconciliationStatus,
    List<UUID> performedAgreementServiceIds,
    long rowVersion) {
  public static ExaminationDetailRowResponse from(ExaminationDetailRow row) {
    return ExaminationDetailRowResponse.builder()
        .id(row.id())
        .participantCode(row.participantCode())
        .fullName(row.fullName())
        .identificationNumberMasked(ParticipantSummaryResponse.mask(row.identificationNumber()))
        .departmentName(row.departmentName())
        .positionName(row.positionName())
        .nextSessionDate(row.nextSessionDate())
        .attendanceStatus(row.attendanceStatus())
        .lastAttendedDate(row.lastAttendedDate())
        .reconciliationStatus(row.reconciliationStatus())
        .performedAgreementServiceIds(row.performedAgreementServiceIds())
        .rowVersion(row.rowVersion())
        .build();
  }
}
