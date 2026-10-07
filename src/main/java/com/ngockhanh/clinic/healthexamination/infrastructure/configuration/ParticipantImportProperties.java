package com.ngockhanh.clinic.healthexamination.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code clinic.participant-import.*} resource budgets of the Participant Excel import. They bound
 * memory and time per request; they are starting budgets, not business rules, and are meant to be
 * tuned after measuring. A non-positive value fails application startup.
 *
 * @param maxFileBytes largest accepted compressed workbook
 * @param maxRows largest accepted data row index; data is only read from worksheet rows 2 to
 *     {@code maxRows + 1}
 * @param maxCellChars longest accepted text in one cell
 * @param maxZipEntries most entries accepted in the workbook package
 * @param maxEntryBytes largest accepted uncompressed size of one package entry
 * @param maxTotalBytes largest accepted total uncompressed size of the package
 */
@ConfigurationProperties("clinic.participant-import")
public record ParticipantImportProperties(
    Long maxFileBytes,
    Integer maxRows,
    Integer maxCellChars,
    Integer maxZipEntries,
    Long maxEntryBytes,
    Long maxTotalBytes) {
  public ParticipantImportProperties {
    maxFileBytes = maxFileBytes == null ? 5L * 1024 * 1024 : maxFileBytes;
    maxRows = maxRows == null ? 1_000 : maxRows;
    maxCellChars = maxCellChars == null ? 500 : maxCellChars;
    maxZipEntries = maxZipEntries == null ? 128 : maxZipEntries;
    maxEntryBytes = maxEntryBytes == null ? 64L * 1024 * 1024 : maxEntryBytes;
    maxTotalBytes = maxTotalBytes == null ? 128L * 1024 * 1024 : maxTotalBytes;
    if (maxFileBytes < 1
        || maxRows < 1
        || maxCellChars < 1
        || maxZipEntries < 1
        || maxEntryBytes < 1
        || maxTotalBytes < 1)
      throw new IllegalArgumentException("Participant import limits must be positive");
  }
}
