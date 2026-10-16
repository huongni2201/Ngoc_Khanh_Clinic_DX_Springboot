package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.integration.application.imports.ImportReceipt;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/**
 * Safe result of a Participant import: identifiers, counts and the completion time. {@code
 * sessionId} and {@code assignedCount} describe the scheduling done in the same commit; the import
 * is all-or-nothing, so when a session was asked for every created Participant was assigned.
 */
@Builder
public record ParticipantImportResponse(
    UUID importJobId,
    UUID agreementId,
    int totalRows,
    int createdCount,
    Instant completedAt,
    UUID sessionId,
    int assignedCount) {
  /** The result of an import that scheduled nobody. */
  public ParticipantImportResponse(
      UUID importJobId, UUID agreementId, int totalRows, int createdCount, Instant completedAt) {
    this(importJobId, agreementId, totalRows, createdCount, completedAt, null, 0);
  }

  public static ParticipantImportResponse from(ImportReceipt receipt) {
    return from(receipt, null);
  }

  /**
   * @param sessionId the session the import scheduled into, or null
   */
  public static ParticipantImportResponse from(ImportReceipt receipt, UUID sessionId) {
    return ParticipantImportResponse.builder()
        .importJobId(receipt.importJobId())
        .agreementId(receipt.agreementId())
        .totalRows(receipt.totalRows())
        .createdCount(receipt.createdCount())
        .completedAt(receipt.completedAt())
        .sessionId(sessionId)
        .assignedCount(sessionId == null ? 0 : receipt.createdCount())
        .build();
  }
}
