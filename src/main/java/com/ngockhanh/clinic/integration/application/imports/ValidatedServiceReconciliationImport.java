package com.ngockhanh.clinic.integration.application.imports;

import java.util.List;
import java.util.UUID;

/**
 * A fully validated examination detail import ready to be recorded.
 *
 * @param templateVersion version of the Excel contract the rows were read with
 */
public record ValidatedServiceReconciliationImport(
    UUID batchId, UUID actorId, int templateVersion, List<StagedReconciliationRow> rows) {
  public ValidatedServiceReconciliationImport {
    if (batchId == null || actorId == null || rows == null || rows.isEmpty())
      throw new IllegalArgumentException("Invalid validated service reconciliation import");
    rows = List.copyOf(rows);
  }
}
