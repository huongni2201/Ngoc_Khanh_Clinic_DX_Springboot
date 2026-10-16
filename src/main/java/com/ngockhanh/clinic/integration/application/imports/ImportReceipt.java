package com.ngockhanh.clinic.integration.application.imports;

import java.time.Instant;
import java.util.UUID;

/** Safe summary of a completed import: identifiers, counts and the completion time. */
public record ImportReceipt(
    UUID importJobId, UUID agreementId, int totalRows, int createdCount, Instant completedAt) {
  public ImportReceipt {
    if (importJobId == null
        || agreementId == null
        || completedAt == null
        || totalRows < 0
        || createdCount < 0) throw new IllegalArgumentException("Invalid import receipt");
  }
}
