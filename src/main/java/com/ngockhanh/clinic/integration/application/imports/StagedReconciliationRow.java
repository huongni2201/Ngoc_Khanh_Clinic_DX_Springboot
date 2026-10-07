package com.ngockhanh.clinic.integration.application.imports;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One validated row staged with its job. It owns the shape of the stored payload, which holds only
 * identifiers, never a name or an identification number.
 *
 * @param actualExaminationDate date typed in the file, or null when the cell was blank
 */
public record StagedReconciliationRow(
    int rowNumber,
    UUID participantId,
    List<UUID> performedBatchServiceIds,
    LocalDate actualExaminationDate) {
  public StagedReconciliationRow {
    if (rowNumber < 1 || participantId == null)
      throw new IllegalArgumentException("Invalid staged reconciliation row");
    performedBatchServiceIds = List.copyOf(performedBatchServiceIds);
  }
}
