package com.ngockhanh.clinic.healthexamination.application.query;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * One data row read from the examination detail workbook, typed but not yet checked against the
 * database.
 *
 * @param rowNumber real one-based row number in the worksheet (the first data row is 3)
 * @param participantId hidden participant identifier the row is matched by
 * @param rowVersion hidden participant row version the file was exported at
 * @param actualExaminationDate actual examination date, or null when the cell is blank
 * @param performedServiceIds batch services marked with X; services left blank are absent
 */
public record ExaminationDetailImportRow(
    int rowNumber,
    UUID participantId,
    long rowVersion,
    LocalDate actualExaminationDate,
    Set<UUID> performedServiceIds) {
  public ExaminationDetailImportRow {
    performedServiceIds = Set.copyOf(performedServiceIds);
  }
}
