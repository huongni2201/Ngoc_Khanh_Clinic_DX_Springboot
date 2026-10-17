package com.ngockhanh.clinic.healthexamination.application.port.out;

import com.ngockhanh.clinic.healthexamination.application.query.ExaminationFormPrintOrder;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read port that chooses the Participants of a session for a bulk form print. */
public interface PrintableParticipantReader {
  /**
   * Reads the active Participants of an agreement who are expected in one session, in print order.
   * Participants who did not show or were cancelled are never included; those who already attended
   * are included only when asked for.
   *
   * @return the session, or empty when the agreement does not belong to the organization, is
   *     deleted, or the session is not an open session the agreement may use
   */
  Optional<PrintableSession> readSession(
      UUID organizationId,
      UUID agreementId,
      UUID sessionId,
      boolean includeAttended,
      ExaminationFormPrintOrder order);

  /**
   * One session and the Participants to print in it.
   *
   * @param sessionDate date of the session
   * @param participantIds identifiers in print order, without duplicates
   */
  record PrintableSession(LocalDate sessionDate, List<UUID> participantIds) {
    public PrintableSession {
      participantIds = List.copyOf(participantIds);
    }
  }
}
