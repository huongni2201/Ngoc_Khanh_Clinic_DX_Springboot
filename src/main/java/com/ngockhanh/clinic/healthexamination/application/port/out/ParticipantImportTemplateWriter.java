package com.ngockhanh.clinic.healthexamination.application.port.out;

/** Produces the supported participant roster workbook without exposing its spreadsheet library. */
public interface ParticipantImportTemplateWriter {
  byte[] generate();
}
