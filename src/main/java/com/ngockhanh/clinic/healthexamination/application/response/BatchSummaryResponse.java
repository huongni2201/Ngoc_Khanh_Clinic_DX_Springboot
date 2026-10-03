package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.BatchSummary;
import java.time.*;
import java.util.UUID;

public record BatchSummaryResponse(
    UUID id,
    String batchCode,
    String batchName,
    LocalDate startDate,
    LocalDate endDate,
    String status,
    Instant createdAt,
    Instant updatedAt) {
  public static BatchSummaryResponse from(BatchSummary b) {
    return new BatchSummaryResponse(
        b.id(),
        b.batchCode(),
        b.batchName(),
        b.startDate(),
        b.endDate(),
        b.status(),
        b.createdAt(),
        b.updatedAt());
  }
}
