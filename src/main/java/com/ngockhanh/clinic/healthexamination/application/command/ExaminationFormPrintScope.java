package com.ngockhanh.clinic.healthexamination.application.command;

/** Which Participants a bulk examination form print covers. */
public enum ExaminationFormPrintScope {
  /** The Participants named by the caller, in the caller's order. */
  IDS,
  /** Every expected Participant of one session, chosen by the backend when the job is created. */
  SESSION
}
