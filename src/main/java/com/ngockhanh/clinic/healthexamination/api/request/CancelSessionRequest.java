package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.CancelSessionCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;

/**
 * Body for cancelling a session nobody is assigned to.
 *
 * @param rowVersion version the client last read; required and not negative
 */
@Builder
public record CancelSessionRequest(@NotNull @PositiveOrZero Long rowVersion) {

  /** Maps the transport body to the command. */
  public CancelSessionCommand toCommand() {
    return CancelSessionCommand.builder().rowVersion(rowVersion).build();
  }
}
