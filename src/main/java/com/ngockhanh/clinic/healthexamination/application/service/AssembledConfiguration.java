package com.ngockhanh.clinic.healthexamination.application.service;

import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchDay;
import com.ngockhanh.clinic.healthexamination.domain.entity.HealthExaminationBatchService;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.ExaminationSite;
import java.util.List;

/**
 * A batch configuration turned into domain objects with identifiers, prices and display order
 * assigned. The batch aggregate still validates it when it is applied.
 */
public record AssembledConfiguration(
    String batchCode,
    String batchName,
    ExaminationSite site,
    List<HealthExaminationBatchDay> days,
    List<HealthExaminationBatchService> services) {
  public AssembledConfiguration {
    days = List.copyOf(days);
    services = List.copyOf(services);
  }
}
