package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.integration.application.imports.ImportReceipt;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/** Safe result of a Participant import: identifiers, counts and the completion time. */
@Builder
public record ParticipantImportResponse(
    UUID importJobId, UUID batchId, int totalRows, int createdCount, Instant completedAt) {
  public static ParticipantImportResponse from(ImportReceipt receipt) {
    return ParticipantImportResponse.builder()
        .importJobId(receipt.importJobId())
        .batchId(receipt.batchId())
        .totalRows(receipt.totalRows())
        .createdCount(receipt.createdCount())
        .completedAt(receipt.completedAt())
        .build();
  }
}
