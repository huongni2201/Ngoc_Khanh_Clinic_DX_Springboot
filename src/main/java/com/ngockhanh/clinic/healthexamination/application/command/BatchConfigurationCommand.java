package com.ngockhanh.clinic.healthexamination.application.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public record BatchConfigurationCommand(
    String batchCode,
    String batchName,
    LocalDate startDate,
    LocalDate endDate,
    String reason,
    String payerType,
    String examinationSiteType,
    String examinationSiteName,
    String examinationSiteAddress,
    List<ServicePrice> services) {
  public BatchConfigurationCommand {
    if (services != null && services.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Missing service");
    services = services == null ? null : List.copyOf(services);
  }

  public record ServicePrice(UUID serviceId, BigDecimal negotiatedUnitPrice) {}
}
