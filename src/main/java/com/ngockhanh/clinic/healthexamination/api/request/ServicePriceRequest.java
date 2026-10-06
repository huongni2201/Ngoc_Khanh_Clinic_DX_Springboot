package com.ngockhanh.clinic.healthexamination.api.request;

import com.ngockhanh.clinic.healthexamination.application.command.BatchConfiguration;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;

/**
 * One catalog service offered in a health examination batch.
 *
 * @param serviceId catalog service identifier
 * @param negotiatedPrice agreed price in VND: not negative, at most 12 integer and 2 fraction
 *     digits
 */
@Builder
public record ServicePriceRequest(
    @NotNull UUID serviceId,
    @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal negotiatedPrice) {

  BatchConfiguration.ServicePrice toServicePrice() {
    return BatchConfiguration.ServicePrice.builder()
        .serviceId(serviceId)
        .negotiatedPrice(negotiatedPrice)
        .build();
  }
}
