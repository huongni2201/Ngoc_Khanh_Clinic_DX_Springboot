package com.ngockhanh.clinic.healthexamination.domain.entity;

import java.time.LocalDate;
import java.util.UUID;

public record HealthExaminationBatchDay(UUID id, LocalDate examinationDate) {
  public HealthExaminationBatchDay {
    if (id == null || examinationDate == null)
      throw new IllegalArgumentException("Invalid batch day");
  }
}
