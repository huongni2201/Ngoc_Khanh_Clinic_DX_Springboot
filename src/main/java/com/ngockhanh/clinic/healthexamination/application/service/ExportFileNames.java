package com.ngockhanh.clinic.healthexamination.application.service;

/**
 * Builds the download name of an exported file. Only the batch code is used, filtered to ASCII
 * letters, digits, underscore and hyphen, so a name never carries personal data or characters that
 * could break a header or a path.
 */
public final class ExportFileNames {
  private static final String FALLBACK_CODE = "batch";

  private ExportFileNames() {}

  /**
   * Returns {@code prefix + filtered batch code + extension}.
   *
   * @param prefix fixed lower-case prefix such as {@code chi-tiet-kham-}
   * @param batchCode business code of the batch; characters outside {@code [A-Za-z0-9_-]} are
   *     dropped, and a code that has none left becomes {@code batch}
   * @param extension fixed extension including the dot
   */
  public static String of(String prefix, String batchCode, String extension) {
    String filtered = batchCode == null ? "" : batchCode.replaceAll("[^A-Za-z0-9_-]", "");
    return prefix + (filtered.isEmpty() ? FALLBACK_CODE : filtered) + extension;
  }
}
