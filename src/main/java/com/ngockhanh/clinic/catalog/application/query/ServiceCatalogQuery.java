package com.ngockhanh.clinic.catalog.application.query;

import java.math.BigDecimal;
import java.util.*;

public interface ServiceCatalogQuery {
  List<Service> findByIds(Set<UUID> ids);

  /**
   * Returns one page of active catalog services.
   *
   * @param pattern lower-case LIKE pattern for code or name, or {@code null} for no search
   * @param sortKey one of {@code id}, {@code code}, {@code name}, {@code unitPrice}
   * @param sortBy {@code ASC} or {@code DESC}
   */
  List<ServiceItem> findActivePage(
      String pattern, long offset, int limit, String sortKey, String sortBy);

  /** Counts the active catalog services that match the same pattern as {@link #findActivePage}. */
  long countActive(String pattern);

  record Service(UUID id, String code, String name, boolean active, BigDecimal unitPrice) {}

  record ServiceItem(
      UUID id,
      String code,
      String name,
      String serviceType,
      BigDecimal unitPrice,
      boolean active) {}
}
