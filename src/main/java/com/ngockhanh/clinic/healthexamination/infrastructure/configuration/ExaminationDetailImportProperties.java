package com.ngockhanh.clinic.healthexamination.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code clinic.examination-detail-import.*} size budgets of the examination detail Excel import,
 * added to the file and package budgets of {@link ParticipantImportProperties}. They bound memory
 * and time per request; they are starting budgets, not business rules, and are meant to be tuned
 * after measuring. A non-positive value fails application startup.
 *
 * @param maxRows most accepted data rows; data is only read from worksheet rows 3 to {@code
 *     maxRows + 2}
 * @param maxServiceColumns most accepted service columns
 */
@ConfigurationProperties("clinic.examination-detail-import")
public record ExaminationDetailImportProperties(Integer maxRows, Integer maxServiceColumns) {
  public ExaminationDetailImportProperties {
    maxRows = maxRows == null ? 10_000 : maxRows;
    maxServiceColumns = maxServiceColumns == null ? 100 : maxServiceColumns;
    if (maxRows < 1 || maxServiceColumns < 1)
      throw new IllegalArgumentException("Examination detail import limits must be positive");
  }
}
