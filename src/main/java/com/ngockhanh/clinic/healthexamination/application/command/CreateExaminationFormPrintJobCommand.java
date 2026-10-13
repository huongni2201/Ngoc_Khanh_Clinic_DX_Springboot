package com.ngockhanh.clinic.healthexamination.application.command;

import java.util.List;
import java.util.UUID;

/**
 * Asks for the examination forms of many Participants of one batch to be printed in the
 * background.
 *
 * @param participantIds the Participants, in print order
 * @param idempotencyKey client-chosen key that makes a retry return the same job
 */
public record CreateExaminationFormPrintJobCommand(List<UUID> participantIds, UUID idempotencyKey) {
  public CreateExaminationFormPrintJobCommand {
    // Not List.copyOf: a null element must be refused as bad input by the job validation.
    participantIds =
        participantIds == null
            ? List.of()
            : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(participantIds));
  }
}
