package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.StartHealthExaminationBatchCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;

/**
 * Body for starting a draft health examination batch.
 *
 * @param rowVersion version the client last read; required and not negative
 */
@Builder
public record StartHealthExaminationBatchRequest(@NotNull @PositiveOrZero Long rowVersion) {

  /** Maps the transport body to the command. */
  public StartHealthExaminationBatchCommand toCommand() {
    return StartHealthExaminationBatchCommand.builder().rowVersion(rowVersion).build();
  }
}
