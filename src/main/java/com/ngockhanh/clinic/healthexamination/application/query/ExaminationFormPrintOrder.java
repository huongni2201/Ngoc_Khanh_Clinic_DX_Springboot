package com.ngockhanh.clinic.healthexamination.application.query;

/** Print order of the Participants of a session. */
public enum ExaminationFormPrintOrder {
  /** Department, then the row order of the import file, then the Participant code. */
  DEPARTMENT,
  /** Participant code. */
  CODE,
  /** Row order of the import file, then the Participant code. */
  IMPORT_ORDER
}
