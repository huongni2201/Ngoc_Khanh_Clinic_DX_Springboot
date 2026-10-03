package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.BatchConfigurationCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public record HealthExaminationBatchRequest(
    @NotBlank @Size(max = 40) String batchCode,
    @NotBlank @Size(max = 250) String batchName,
    LocalDate startDate,
    LocalDate endDate,
    @Size(max = 300) String reason,
    @Size(max = 24) String payerType,
    @NotNull @Pattern(regexp = "CLINIC|COMPANY") String examinationSiteType,
    @NotBlank @Size(max = 250) String examinationSiteName,
    @Size(max = 500) String examinationSiteAddress,
    @NotEmpty List<@NotNull @Valid ServicePriceRequest> services
) {

  public record ServicePriceRequest(
      @NotNull UUID serviceId,
      @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2)
          BigDecimal negotiatedUnitPrice) {}

  public BatchConfigurationCommand toCommand() {
    return new BatchConfigurationCommand(
        batchCode,
        batchName,
        startDate,
        endDate,
        reason,
        payerType,
        examinationSiteType,
        examinationSiteName,
        examinationSiteAddress,
        services.stream()
            .map(
                s ->
                    new BatchConfigurationCommand.ServicePrice(
                        s.serviceId(), s.negotiatedUnitPrice()))
            .toList());
  }
}
