package com.ngockhanh.clinic.healthexamination.application.response;

import com.ngockhanh.clinic.integration.application.imports.ServiceReconciliationReceipt;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/**
 * Safe result of an examination detail import: identifiers, counts and the completion time.
 *
 * @param totalRows data rows of the file
 * @param updatedParticipants Participants whose attendance or services were written
 * @param unchangedParticipants Participants of the file that needed no write
 * @param performedItems services marked performed across the whole file
 */
@Builder
public record ExaminationDetailImportResponse(
    UUID importJobId,
    UUID batchId,
    int totalRows,
    int updatedParticipants,
    int unchangedParticipants,
    int performedItems,
    Instant completedAt) {
  public static ExaminationDetailImportResponse from(ServiceReconciliationReceipt receipt) {
    return ExaminationDetailImportResponse.builder()
        .importJobId(receipt.importJobId())
        .batchId(receipt.batchId())
        .totalRows(receipt.totalRows())
        .updatedParticipants(receipt.updatedParticipants())
        .unchangedParticipants(receipt.unchangedParticipants())
        .performedItems(receipt.performedItems())
        .completedAt(receipt.completedAt())
        .build();
  }
}
