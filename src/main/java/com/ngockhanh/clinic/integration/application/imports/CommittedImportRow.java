package com.ngockhanh.clinic.integration.application.imports;

import java.util.UUID;

/** The resource created from one staged row. */
public record CommittedImportRow(int rowNumber, String resourceType, UUID resourceId) {
  public CommittedImportRow {
    if (rowNumber < 1 || resourceType == null || resourceType.isBlank() || resourceId == null)
      throw new IllegalArgumentException("Invalid committed import row");
  }
}
