package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.RecordExaminationDetailCommand;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Builder;

/**
 * Body of recording the services performed for one Participant by hand.
 *
 * @param performedAgreementServiceIds agreement services performed, without duplicates; empty means
 *     nothing performed
 * @param rowVersion version the client last read; required and not negative
 */
@Builder
public record RecordExaminationDetailRequest(
    @NotNull @Size(max = MAX_SERVICES) List<@NotNull UUID> performedAgreementServiceIds,
    @NotNull @PositiveOrZero Long rowVersion) {
  static final int MAX_SERVICES = 100;

  /**
   * Maps the validated body to the application command.
   *
   * @throws ApplicationException {@code INVALID_INPUT} when a service is listed twice
   */
  public RecordExaminationDetailCommand toCommand() {
    Set<UUID> services = new HashSet<>(performedAgreementServiceIds);
    if (services.size() != performedAgreementServiceIds.size())
      throw new ApplicationException(
          ApplicationException.Type.INVALID_INPUT,
          "performedAgreementServiceIds must not contain duplicates");
    return new RecordExaminationDetailCommand(services, rowVersion);
  }
}
