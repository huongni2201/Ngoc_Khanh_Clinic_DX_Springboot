package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.BatchConfigurationCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import lombok.Builder;

@Builder
public record HealthExaminationBatchRequest(
		@NotBlank @Size(max = 50) String batchCode,
		@NotBlank @Size(max = 300) String batchName,
		@NotEmpty List<@NotNull LocalDate> examinationDates,
		@NotNull @Pattern(regexp = "CLINIC|ORGANIZATION_SITE") String examinationSiteType,
		@NotBlank String examinationSiteName,
		@NotBlank String examinationSiteAddress,
		@NotEmpty List<@NotNull @Valid ServicePriceRequest> services) {
	@Builder
	public record ServicePriceRequest(
			@NotNull UUID serviceId,
			@NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal negotiatedPrice) {
	}

	public BatchConfigurationCommand toCommand() {
		return BatchConfigurationCommand.builder()
				.batchCode(batchCode)
				.batchName(batchName)
				.examinationDates(examinationDates)
				.examinationSiteType(examinationSiteType)
				.examinationSiteName(examinationSiteName)
				.examinationSiteAddress(examinationSiteAddress)
				.services(
						services.stream()
								.map(
										s ->
												BatchConfigurationCommand.ServicePrice.builder()
														.serviceId(s.serviceId())
														.negotiatedPrice(s.negotiatedPrice())
														.build())
								.toList())
				.build();
	}
}
