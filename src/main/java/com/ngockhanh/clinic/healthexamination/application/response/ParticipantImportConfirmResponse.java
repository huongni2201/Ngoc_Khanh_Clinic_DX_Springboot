package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.healthexamination.domain.aggregate.HealthExaminationImportJob;
import java.util.UUID;

public record ParticipantImportConfirmResponse(UUID importId, String status, int importedRows) {
  public static ParticipantImportConfirmResponse from(HealthExaminationImportJob job) {
    if (!job.isConfirmed()) throw new IllegalArgumentException("Import is not confirmed");
    return new ParticipantImportConfirmResponse(
        job.id().value(), job.status().name(), job.confirmedResult().importedRows());
  }
}
