package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.CreateSessionCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;

/**
 * Body for creating a session. The status and the Participant count are never accepted from the
 * client.
 *
 * @param agreementId the only agreement the session serves; omit it for a session shared by every
 *     agreement of the organization
 */
@Builder
public record CreateSessionRequest(
    UUID agreementId,
    @NotNull LocalDate sessionDate,
    @NotNull @Pattern(regexp = "ALL|SAMPLING|EXAMINATION") String activity,
    @NotNull @Pattern(regexp = "CLINIC|ORGANIZATION_SITE") String siteType,
    String siteAddress) {

  @AssertTrue(message = "Site address is required for an organization site")
  public boolean isSiteAddressValid() {
    return !"ORGANIZATION_SITE".equals(siteType) || (siteAddress != null && !siteAddress.isBlank());
  }

  /** Maps the transport body to the command. */
  public CreateSessionCommand toCommand() {
    return CreateSessionCommand.builder()
        .agreementId(agreementId)
        .sessionDate(sessionDate)
        .activity(activity)
        .siteType(siteType)
        .siteAddress(siteAddress)
        .build();
  }
}
