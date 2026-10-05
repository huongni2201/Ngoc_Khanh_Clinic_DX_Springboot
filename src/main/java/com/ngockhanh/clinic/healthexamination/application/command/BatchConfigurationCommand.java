package com.ngockhanh.clinic.healthexamination.application.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public record BatchConfigurationCommand(
    String batchCode,
    String batchName,
    List<LocalDate> examinationDates,
    String examinationSiteType,
    String examinationSiteName,
    String examinationSiteAddress,
    List<ServicePrice> services) {
  public BatchConfigurationCommand {
    examinationDates = examinationDates == null ? null : List.copyOf(examinationDates);
    services = services == null ? null : List.copyOf(services);
  }

  public record ServicePrice(UUID serviceId, BigDecimal negotiatedPrice) {}
}
