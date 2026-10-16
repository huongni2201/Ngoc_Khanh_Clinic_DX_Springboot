package com.ngockhanh.clinic.integration.application.imports;

import java.util.List;
import java.util.UUID;

/**
 * One validated row staged with its job. It owns the shape of the stored payload, which holds only
 * identifiers, never a name or an identification number.
 */
public record StagedReconciliationRow(
    int rowNumber, UUID participantId, List<UUID> performedAgreementServiceIds) {
  public StagedReconciliationRow {
    if (rowNumber < 1 || participantId == null)
      throw new IllegalArgumentException("Invalid staged reconciliation row");
    performedAgreementServiceIds = List.copyOf(performedAgreementServiceIds);
  }
}
