package com.ngockhanh.clinic.integration.application.imports;

import java.util.List;
import java.util.UUID;

/**
 * A fully validated Participant import ready to be recorded.
 *
 * @param templateVersion version of the Excel contract the rows were read with
 * @param expectedBatchVersion batch configuration version the caller expected
 */
public record ValidatedParticipantImport(
    UUID batchId,
    UUID actorId,
    int templateVersion,
    long expectedBatchVersion,
    List<StagedParticipantRow> rows) {
  public ValidatedParticipantImport {
    if (batchId == null || actorId == null || rows == null || rows.isEmpty())
      throw new IllegalArgumentException("Invalid validated import");
    rows = List.copyOf(rows);
  }
}
