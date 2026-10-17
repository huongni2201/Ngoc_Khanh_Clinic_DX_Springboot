package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.CreateExaminationFormPrintJobCommand;
import com.ngockhanh.clinic.healthexamination.application.command.ExaminationFormPrintScope;
import com.ngockhanh.clinic.healthexamination.application.query.ExaminationFormPrintOrder;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Body for printing the examination forms of many Participants. Only the structure is checked here;
 * which fields a scope needs, the number of records allowed and the agreement rules are checked by
 * the application.
 *
 * @param scope {@code IDS} (default) prints {@code participantIds}; {@code SESSION} prints the
 *     expected Participants of {@code sessionId}, chosen by the backend when the job is created
 * @param participantIds for {@code IDS}: the Participants to print, in print order, without
 *     duplicates
 * @param sessionId for {@code SESSION}: the session to print; for {@code IDS}: optional, the
 *     session whose date is printed on the forms
 * @param includeAttended for {@code SESSION}: also print Participants who already attended
 *     (reprint); default false
 * @param sort for {@code SESSION}: print order, {@code DEPARTMENT} by default
 */
@Builder
public record CreateExaminationFormPrintJobRequest(
    ExaminationFormPrintScope scope,
    @Size(max = 5000) List<@NotNull UUID> participantIds,
    UUID sessionId,
    Boolean includeAttended,
    ExaminationFormPrintOrder sort) {

  /** Structure check: a listed print needs Participants, a day print needs the day. */
  @AssertTrue(message = "Participants (scope IDS) or sessionId (scope SESSION) are required")
  public boolean isTargetPresent() {
    if (scope == ExaminationFormPrintScope.SESSION) return sessionId != null;
    return participantIds != null && !participantIds.isEmpty();
  }

  /** Maps the transport body and the {@code Idempotency-Key} header to the command. */
  public CreateExaminationFormPrintJobCommand toCommand(UUID idempotencyKey) {
    return new CreateExaminationFormPrintJobCommand(
        scope == null ? ExaminationFormPrintScope.IDS : scope,
        participantIds,
        sessionId,
        Boolean.TRUE.equals(includeAttended),
        sort == null ? ExaminationFormPrintOrder.DEPARTMENT : sort,
        idempotencyKey);
  }
}
