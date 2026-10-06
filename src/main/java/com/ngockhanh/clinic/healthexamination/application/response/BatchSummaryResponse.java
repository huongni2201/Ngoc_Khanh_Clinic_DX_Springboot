package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository.BatchSummary;
import java.time.*;
import java.util.UUID;
import lombok.Builder;

@Builder
public record BatchSummaryResponse(
    UUID id,
    String batchCode,
    String batchName,
    LocalDate startDate,
    LocalDate endDate,
    String status,
    Instant createdAt,
    Instant updatedAt,
    long rowVersion) {
  public static BatchSummaryResponse from(BatchSummary b) {
    return BatchSummaryResponse.builder()
        .id(b.id())
        .batchCode(b.batchCode())
        .batchName(b.batchName())
        .startDate(b.startDate())
        .endDate(b.endDate())
        .status(b.status())
        .createdAt(b.createdAt())
        .updatedAt(b.updatedAt())
        .rowVersion(b.rowVersion())
        .build();
  }
}
