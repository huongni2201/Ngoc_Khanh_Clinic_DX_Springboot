package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.UpdateSessionCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import lombok.Builder;

/**
 * Body for changing the date, activity and site of an open session.
 *
 * @param rowVersion version the client last read; required and not negative
 */
@Builder
public record UpdateSessionRequest(
    @NotNull LocalDate sessionDate,
    @NotNull @Pattern(regexp = "ALL|SAMPLING|EXAMINATION") String activity,
    @NotNull @Pattern(regexp = "CLINIC|ORGANIZATION_SITE") String siteType,
    String siteAddress,
    @NotNull @PositiveOrZero Long rowVersion) {

  @AssertTrue(message = "Site address is required for an organization site")
  public boolean isSiteAddressValid() {
    return !"ORGANIZATION_SITE".equals(siteType) || (siteAddress != null && !siteAddress.isBlank());
  }

  /** Maps the transport body to the command. */
  public UpdateSessionCommand toCommand() {
    return UpdateSessionCommand.builder()
        .sessionDate(sessionDate)
        .activity(activity)
        .siteType(siteType)
        .siteAddress(siteAddress)
        .rowVersion(rowVersion)
        .build();
  }
}
