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
 * Body for printing the examination forms of many Participants. Only the structure is checked
 * here; which fields a scope needs, the number of records allowed and the batch rules are checked
 * by the application.
 *
 * @param scope {@code IDS} (default) prints {@code participantIds}; {@code BATCH_DAY} prints the
 *     Participants of {@code batchDayId}, chosen by the backend when the job is created
 * @param participantIds for {@code IDS}: the Participants to print, in print order, without
 *     duplicates
 * @param batchDayId for {@code BATCH_DAY}: the examination day to print
 * @param includeAttended for {@code BATCH_DAY}: also print Participants who already attended
 *     (reprint); default false
 * @param sort for {@code BATCH_DAY}: print order, {@code DEPARTMENT} by default
 */
@Builder
public record CreateExaminationFormPrintJobRequest(
    ExaminationFormPrintScope scope,
    @Size(max = 5000) List<@NotNull UUID> participantIds,
    UUID batchDayId,
    Boolean includeAttended,
    ExaminationFormPrintOrder sort) {

  /** Structure check: a listed print needs Participants, a day print needs the day. */
  @AssertTrue(message = "Participants (scope IDS) or batchDayId (scope BATCH_DAY) are required")
  public boolean isTargetPresent() {
    if (scope == ExaminationFormPrintScope.BATCH_DAY) return batchDayId != null;
    return participantIds != null && !participantIds.isEmpty();
  }

  /** Maps the transport body and the {@code Idempotency-Key} header to the command. */
  public CreateExaminationFormPrintJobCommand toCommand(UUID idempotencyKey) {
    return new CreateExaminationFormPrintJobCommand(
        scope == null ? ExaminationFormPrintScope.IDS : scope,
        participantIds,
        batchDayId,
        Boolean.TRUE.equals(includeAttended),
        sort == null ? ExaminationFormPrintOrder.DEPARTMENT : sort,
        idempotencyKey);
  }
}
