package com.ngockhanh.clinic.healthexamination.application.query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything the Excel writer needs to render the examination detail workbook of one batch.
 *
 * @param services service columns in display order, inactive services included
 * @param rows every active Participant of the batch in export order; the identification number of
 *     each row is already masked, so the writer never sees a complete one
 */
public record ExaminationDetailExportData(
    UUID batchId,
    String batchCode,
    Instant exportedAt,
    List<ExaminationServiceColumn> services,
    List<ExaminationDetailRow> rows) {
  public ExaminationDetailExportData {
    services = List.copyOf(services);
    rows = List.copyOf(rows);
  }
}
