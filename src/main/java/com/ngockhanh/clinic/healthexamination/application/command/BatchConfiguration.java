package com.ngockhanh.clinic.healthexamination.application.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * Configuration shared by creating and updating a health examination batch.
 *
 * <p>Lists are copied defensively. Values are validated where they are interpreted: the assembler
 * and the batch aggregate.
 *
 * @param batchCode unique batch code
 * @param batchName batch display name
 * @param examinationDates examination dates, without duplicates
 * @param examinationSiteType {@code CLINIC} or {@code ORGANIZATION_SITE}
 * @param examinationSiteName site name
 * @param examinationSiteAddress site address
 * @param services catalog services with their negotiated price; list order is the display order
 */
@Builder(toBuilder = true)
public record BatchConfiguration(
    String batchCode,
    String batchName,
    List<LocalDate> examinationDates,
    String examinationSiteType,
    String examinationSiteName,
    String examinationSiteAddress,
    List<ServicePrice> services) {
  public BatchConfiguration {
    examinationDates = copy(examinationDates);
    services = copy(services);
  }

  private static <T> List<T> copy(List<T> source) {
    return source == null ? null : Collections.unmodifiableList(new ArrayList<>(source));
  }

  /**
   * A catalog service and the price agreed for this batch.
   *
   * @param serviceId catalog service identifier
   * @param negotiatedPrice agreed price in VND
   */
  @Builder
  public record ServicePrice(UUID serviceId, BigDecimal negotiatedPrice) {}
}
