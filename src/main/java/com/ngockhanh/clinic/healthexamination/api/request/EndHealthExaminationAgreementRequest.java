package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.EndHealthExaminationAgreementCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;

/**
 * Body for ending an active health examination agreement.
 *
 * @param rowVersion version the client last read; required and not negative
 */
@Builder
public record EndHealthExaminationAgreementRequest(@NotNull @PositiveOrZero Long rowVersion) {

  /** Maps the transport body to the command. */
  public EndHealthExaminationAgreementCommand toCommand() {
    return EndHealthExaminationAgreementCommand.builder().rowVersion(rowVersion).build();
  }
}
