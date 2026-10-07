package com.ngockhanh.clinic.healthexamination.application.port.out;

import com.ngockhanh.clinic.healthexamination.application.query.ParticipantTemplateData;

/** Renders the Excel import template of a batch. */
public interface ParticipantTemplateWriter {
  /** Returns the bytes of the XLSX template. */
  byte[] write(ParticipantTemplateData data);
}
