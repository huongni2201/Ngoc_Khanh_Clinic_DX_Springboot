package com.ngockhanh.clinic.healthexamination.application.query;

import java.util.List;
import java.util.UUID;

/**
 * Typed content of an import workbook: the template metadata that helps the user match the file to
 * a batch, and the data rows. The metadata is advisory; the path and the database stay the source
 * of truth.
 */
public record ParticipantWorkbook(
    int templateVersion,
    UUID templateBatchId,
    long templateBatchVersion,
    List<ParticipantImportRow> rows) {
  /** Version of the Excel contract this release reads and writes. */
  public static final int TEMPLATE_VERSION = 1;

  public ParticipantWorkbook {
    rows = List.copyOf(rows);
  }
}
