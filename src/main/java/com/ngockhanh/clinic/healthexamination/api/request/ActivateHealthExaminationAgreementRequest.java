package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.ActivateHealthExaminationAgreementCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;

/**
 * Body for activating a draft health examination agreement.
 *
 * @param rowVersion version the client last read; required and not negative
 */
@Builder
public record ActivateHealthExaminationAgreementRequest(@NotNull @PositiveOrZero Long rowVersion) {

  /** Maps the transport body to the command. */
  public ActivateHealthExaminationAgreementCommand toCommand() {
    return ActivateHealthExaminationAgreementCommand.builder().rowVersion(rowVersion).build();
  }
}
