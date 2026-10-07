package com.ngockhanh.clinic.integration.application.imports;

import java.time.Instant;
import java.util.UUID;

/**
 * Safe summary of a completed examination detail import: identifiers, counts and the completion
 * time.
 *
 * @param totalRows data rows of the file, including rows that needed no write
 * @param updatedParticipants Participants that were written
 * @param unchangedParticipants Participants of the file that needed no write
 * @param performedItems services marked performed across the whole file
 */
public record ServiceReconciliationReceipt(
    UUID importJobId,
    UUID batchId,
    int totalRows,
    int updatedParticipants,
    int unchangedParticipants,
    int performedItems,
    Instant completedAt) {
  public ServiceReconciliationReceipt {
    if (importJobId == null
        || batchId == null
        || completedAt == null
        || totalRows < 0
        || updatedParticipants < 0
        || unchangedParticipants < 0
        || performedItems < 0
        || updatedParticipants + unchangedParticipants > totalRows)
      throw new IllegalArgumentException("Invalid service reconciliation receipt");
  }
}
