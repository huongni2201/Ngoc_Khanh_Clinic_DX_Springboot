package com.ngockhanh.clinic.healthexamination.application.query;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Typed content of an examination detail workbook: the template version, the batch services the
 * file declares in its machine-key row and the data rows. The declared services are compared with
 * the batch by the use case; the file is never trusted to choose the batch.
 */
public record ExaminationDetailWorkbook(
    int templateVersion, Set<UUID> declaredServiceIds, List<ExaminationDetailImportRow> rows) {
  /** Version of the examination detail Excel contract this release reads and writes. */
  public static final int TEMPLATE_VERSION = 1;

  public ExaminationDetailWorkbook {
    declaredServiceIds = Set.copyOf(declaredServiceIds);
    rows = List.copyOf(rows);
  }
}
