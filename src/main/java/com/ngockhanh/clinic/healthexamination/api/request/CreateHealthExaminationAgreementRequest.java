package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.AgreementConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/**
 * Body for creating a health examination agreement of an organization.
 *
 * <p>The agreement code, status, creator, reference-price snapshots, display order and child
 * identifiers are never accepted from the client: the system generates the code on create. The
 * validity period is optional; examination days and sites belong to sessions.
 */
@Builder
public record CreateHealthExaminationAgreementRequest(
    @NotBlank @Size(max = 300) String agreementName,
    LocalDate validFrom,
    LocalDate validTo,
    @NotEmpty List<@NotNull @Valid ServicePriceRequest> services) {

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
