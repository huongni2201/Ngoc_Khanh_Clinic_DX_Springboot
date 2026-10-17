package com.ngockhanh.clinic.healthexamination.application.command;

import com.ngockhanh.clinic.healthexamination.application.query.ExaminationFormPrintOrder;
import java.util.List;
import java.util.UUID;

/**
 * Asks for the examination forms of many Participants of one agreement to be printed in the
 * background.
 *
 * @param scope {@code IDS} prints {@code participantIds}; {@code SESSION} prints the expected
 *     Participants of {@code sessionId}
 * @param participantIds for {@code IDS}: the Participants, in print order
 * @param sessionId for {@code SESSION}: the session; for {@code IDS}: optional, the session whose
 *     date is printed on the forms
 * @param includeAttended for {@code SESSION}: also print Participants who already attended
 * @param sort for {@code SESSION}: print order
 * @param idempotencyKey client-chosen key that makes a retry return the same job
 */
public record CreateExaminationFormPrintJobCommand(
    ExaminationFormPrintScope scope,
    List<UUID> participantIds,
    UUID sessionId,
    boolean includeAttended,
    ExaminationFormPrintOrder sort,
    UUID idempotencyKey) {
  public CreateExaminationFormPrintJobCommand {
    // Not List.copyOf: a null element must be refused as bad input by the job validation.
    participantIds =
        participantIds == null
            ? List.of()
            : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(participantIds));
  }

  /** Prints the named Participants, in the given order. */
  public static CreateExaminationFormPrintJobCommand ofIds(
      List<UUID> participantIds, UUID idempotencyKey) {
    return new CreateExaminationFormPrintJobCommand(
        ExaminationFormPrintScope.IDS,
        participantIds,
        null,
        false,
        ExaminationFormPrintOrder.DEPARTMENT,
        idempotencyKey);
  }
}
