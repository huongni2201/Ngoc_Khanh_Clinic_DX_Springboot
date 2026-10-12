package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.BatchConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

/**
 * Body for creating a health examination batch of an organization.
 *
 * <p>The batch code, status, creator, reference-price snapshots, display order and child
 * identifiers are never accepted from the client: the system generates the code on create.
 */
@Builder
public record CreateHealthExaminationBatchRequest(
    @NotBlank @Size(max = 300) String batchName,
    @NotEmpty List<@NotNull LocalDate> examinationDates,
    @NotNull @Pattern(regexp = "CLINIC|ORGANIZATION_SITE") String examinationSiteType,
    String examinationSiteAddress,
    @NotEmpty List<@NotNull @Valid ServicePriceRequest> services) {

  @AssertTrue(message = "Examination site address is required for an organization site")
  public boolean isSiteAddressValid() {
    return !"ORGANIZATION_SITE".equals(examinationSiteType)
        || (examinationSiteAddress != null && !examinationSiteAddress.isBlank());
  }

  /** Maps the transport body to the application configuration input. */
  public BatchConfiguration toConfiguration() {
    return BatchConfiguration.builder()
        .batchName(trimmed(batchName))
        .examinationDates(examinationDates)
        .examinationSiteType(examinationSiteType)
        .examinationSiteAddress(trimmed(examinationSiteAddress))
        .services(services.stream().map(ServicePriceRequest::toServicePrice).toList())
        .build();
  }

  private static String trimmed(String value) {
    return value == null ? null : value.trim();
  }
}
