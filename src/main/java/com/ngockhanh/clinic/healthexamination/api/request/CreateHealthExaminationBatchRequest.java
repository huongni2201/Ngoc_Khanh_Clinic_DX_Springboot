package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.BatchConfiguration;
import jakarta.validation.Valid;
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
 * <p>Status, creator, reference-price snapshots, display order and child identifiers are never
 * accepted from the client.
 */
@Builder
public record CreateHealthExaminationBatchRequest(
    @NotBlank @Size(max = 50) String batchCode,
    @NotBlank @Size(max = 300) String batchName,
    @NotEmpty List<@NotNull LocalDate> examinationDates,
    @NotNull @Pattern(regexp = "CLINIC|ORGANIZATION_SITE") String examinationSiteType,
    @NotBlank String examinationSiteName,
    @NotBlank String examinationSiteAddress,
    @NotEmpty List<@NotNull @Valid ServicePriceRequest> services) {

  /** Maps the transport body to the application configuration input. */
  public BatchConfiguration toConfiguration() {
    return BatchConfiguration.builder()
        .batchCode(batchCode)
        .batchName(batchName)
        .examinationDates(examinationDates)
        .examinationSiteType(examinationSiteType)
        .examinationSiteName(examinationSiteName)
        .examinationSiteAddress(examinationSiteAddress)
        .services(services.stream().map(ServicePriceRequest::toServicePrice).toList())
        .build();
  }
}
