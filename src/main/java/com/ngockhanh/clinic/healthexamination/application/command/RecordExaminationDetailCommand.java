package com.ngockhanh.clinic.healthexamination.application.command;

import java.util.Set;
import java.util.UUID;

/**
 * Input of recording the services performed for one Participant by hand.
 *
 * <p>Attendance is not part of it: it is recorded per session assignment. The command only carries
 * the data; the rules are checked by the use case, so they hold for every caller.
 *
 * @param performedAgreementServiceIds agreement services performed; none means nothing performed
 * @param rowVersion Participant row version the client last read
 */
public record RecordExaminationDetailCommand(
    Set<UUID> performedAgreementServiceIds, long rowVersion) {
  public RecordExaminationDetailCommand {
    performedAgreementServiceIds =
        performedAgreementServiceIds == null ? Set.of() : Set.copyOf(performedAgreementServiceIds);
  }
}
