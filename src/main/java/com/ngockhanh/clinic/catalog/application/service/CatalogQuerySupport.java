package com.ngockhanh.clinic.catalog.application.service;

import java.util.Locale;

public final class CatalogQuerySupport {
  public static final int MAX_SEARCH_KEY_LENGTH = 100;

  private CatalogQuerySupport() {}

  public static String likePattern(String searchKey) {
    if (searchKey == null || searchKey.isBlank()) return null;
    String trimmed = searchKey.trim();
    if (trimmed.length() > MAX_SEARCH_KEY_LENGTH)
      throw new IllegalArgumentException("Search key is too long");
    String escaped =
        trimmed
            .toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    return "%" + escaped + "%";
  }

  public static <E extends Enum<E>> String enumFilter(String value, Class<E> type) {
    if (value == null || value.isBlank()) return null;
    try {
      return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT)).name();
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Invalid catalog type filter");
    }
  }
}
