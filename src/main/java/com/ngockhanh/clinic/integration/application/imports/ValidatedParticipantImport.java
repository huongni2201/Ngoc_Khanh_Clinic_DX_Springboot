package com.ngockhanh.clinic.integration.application.imports;

import java.util.List;
import java.util.UUID;

/**
 * A fully validated Participant import ready to be recorded.
 *
 * @param templateVersion version of the Excel contract the rows were read with
 * @param expectedAgreementVersion agreement configuration version the caller expected
 */
public record ValidatedParticipantImport(
    UUID agreementId,
    UUID actorId,
    int templateVersion,
    long expectedAgreementVersion,
    List<StagedParticipantRow> rows) {
  public ValidatedParticipantImport {
    if (agreementId == null || actorId == null || rows == null || rows.isEmpty())
      throw new IllegalArgumentException("Invalid validated import");
    rows = List.copyOf(rows);
  }
}
