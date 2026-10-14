package com.ngockhanh.clinic.healthexamination.application.port.out;

import com.ngockhanh.clinic.healthexamination.application.query.ExaminationFormPrintOrder;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read port that chooses the Participants of an examination day for a bulk form print. */
public interface PrintableParticipantReader {
  /**
   * Reads the active Participants of one examination day in print order. Participants who are
   * marked absent or cancelled are never included; those who already attended are included only
   * when asked for.
   *
   * @return the day, or empty when the batch does not belong to the organization, is deleted, or
   *     the day is not a day of that batch
   */
  Optional<PrintableDay> readDay(
      UUID organizationId,
      UUID batchId,
      UUID batchDayId,
      boolean includeAttended,
      ExaminationFormPrintOrder order);

  /**
   * One examination day and the Participants to print on it.
   *
   * @param examinationDate date of the day
   * @param participantIds identifiers in print order, without duplicates
   */
  record PrintableDay(LocalDate examinationDate, List<UUID> participantIds) {
    public PrintableDay {
      participantIds = List.copyOf(participantIds);
    }
  }
}
