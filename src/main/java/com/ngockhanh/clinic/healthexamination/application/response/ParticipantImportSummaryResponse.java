package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import com.ngockhanh.clinic.healthexamination.domain.enums.ImportStatus;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import java.util.List;
import java.util.UUID;

public record ParticipantImportSummaryResponse(
    UUID importId,
    String status,
    long rowVersion,
    int totalRows,
    boolean confirmAllowed,
    List<UUID> selectedBatchDayIds) {
  public static ParticipantImportSummaryResponse from(
      HealthExaminationImportJob job, java.time.Instant now) {
    return new ParticipantImportSummaryResponse(
        job.id().value(),
        job.status().name(),
        job.rowVersion(),
        job.rows().size(),
        job.status() == ImportStatus.VALIDATED
            && (job.expiresAt() == null || job.expiresAt().isAfter(now)),
        job.selectedBatchDayIds().stream().map(AggregateId::value).toList());
  }
}
