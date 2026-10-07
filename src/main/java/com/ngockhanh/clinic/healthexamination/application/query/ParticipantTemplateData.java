package com.ngockhanh.clinic.healthexamination.application.query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What the template of one batch carries: the batch it belongs to, the configuration version it was
 * issued for and the valid examination dates in ascending order.
 */
public record ParticipantTemplateData(
    UUID batchId, long batchRowVersion, List<LocalDate> examinationDates) {
  public ParticipantTemplateData {
    examinationDates = List.copyOf(examinationDates);
  }
}
