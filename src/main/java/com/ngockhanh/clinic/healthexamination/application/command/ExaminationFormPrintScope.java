package com.ngockhanh.clinic.healthexamination.application.command;

/** Which Participants a bulk examination form print covers. */
public enum ExaminationFormPrintScope {
  /** The Participants named by the caller, in the caller's order. */
  IDS,
  /** Every Participant of one examination day, chosen by the backend when the job is created. */
  BATCH_DAY
}
