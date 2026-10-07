package com.ngockhanh.clinic.healthexamination.application.port;

import com.ngockhanh.clinic.healthexamination.application.query.ExaminationDetailExportData;

/** Renders the examination detail workbook of a batch; it is also the template for the import. */
public interface ExaminationDetailExcelWriter {
  /** Returns the bytes of the XLSX workbook. */
  byte[] write(ExaminationDetailExportData data);
}
