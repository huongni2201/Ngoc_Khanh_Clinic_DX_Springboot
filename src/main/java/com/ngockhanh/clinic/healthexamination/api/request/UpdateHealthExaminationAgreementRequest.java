package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.AgreementConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/**
 * Body for replacing the whole configuration of a draft health examination agreement.
 *
 * <p>This is a full replacement (PUT), not a partial update. {@code rowVersion} is the header
 * version the client last read. The agreement code is not part of the body: it never changes after
 * creation.
 */
@Builder
public record UpdateHealthExaminationAgreementRequest(
    @NotBlank @Size(max = 300) String agreementName,
    LocalDate validFrom,
    LocalDate validTo,
    @NotEmpty List<@NotNull @Valid ServicePriceRequest> services,
    @NotNull @PositiveOrZero Long rowVersion) {

  @AssertTrue(message = "Validity start must not be after its end")
  public boolean isValidityPeriodValid() {
    return validFrom == null || validTo == null || !validFrom.isAfter(validTo);
  }

  /** Maps the transport body to the application configuration input. */
  public AgreementConfiguration toConfiguration() {
    return AgreementConfiguration.builder()
        .agreementName(agreementName == null ? null : agreementName.trim())
        .validFrom(validFrom)
        .validTo(validTo)
        .services(services.stream().map(ServicePriceRequest::toServicePrice).toList())
        .build();
  }
}
