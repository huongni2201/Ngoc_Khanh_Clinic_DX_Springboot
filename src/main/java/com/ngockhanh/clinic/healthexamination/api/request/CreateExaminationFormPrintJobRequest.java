package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.CreateExaminationFormPrintJobCommand;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Body for printing the examination forms of many Participants. Only the structure is checked
 * here; the number of records allowed and the batch rules are checked by the application.
 *
 * @param participantIds the Participants to print, in print order, without duplicates
 */
@Builder
public record CreateExaminationFormPrintJobRequest(
    @NotEmpty @Size(max = 5000) List<@NotNull UUID> participantIds) {

  /** Maps the transport body and the {@code Idempotency-Key} header to the command. */
  public CreateExaminationFormPrintJobCommand toCommand(UUID idempotencyKey) {
    return new CreateExaminationFormPrintJobCommand(participantIds, idempotencyKey);
  }
}
