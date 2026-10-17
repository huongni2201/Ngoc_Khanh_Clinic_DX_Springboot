package com.ngockhanh.clinic.healthexamination.application.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Configuration shared by creating and updating a health examination agreement.
 *
 * <p>Lists are copied defensively. Values are validated where they are interpreted: the use case
 * and the agreement aggregate.
 *
 * @param agreementName agreement display name
 * @param validFrom first day the agreement applies, or null
 * @param validTo last day the agreement applies, or null
 * @param services catalog services with their negotiated price; list order is the display order
 */
@Builder(toBuilder = true)
public record AgreementConfiguration(
    String agreementName, LocalDate validFrom, LocalDate validTo, List<ServicePrice> services) {
  public AgreementConfiguration {
    services = services == null ? null : Collections.unmodifiableList(new ArrayList<>(services));
  }

  /**
   * A catalog service and the price agreed for this agreement.
   *
   * @param serviceId catalog service identifier
   * @param negotiatedPrice agreed price in VND
   */
  @Builder
  public record ServicePrice(UUID serviceId, BigDecimal negotiatedPrice) {}
}
