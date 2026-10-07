package com.ngockhanh.clinic.healthexamination.application.port;

import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailWorkbook;

/** Parses an examination detail workbook into typed rows. It never touches the database. */
public interface ExaminationDetailExcelReader {
  /**
   * Reads the workbook bytes.
   *
   * @throws com.ngockhanh.clinic.shared.exception.ApplicationException of type {@code
   *     INVALID_INPUT} with a safe message naming the Excel row and field when the file or a cell
   *     is not acceptable
   */
  ExaminationDetailWorkbook read(byte[] workbook);
}
