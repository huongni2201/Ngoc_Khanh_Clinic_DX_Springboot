package com.ngockhanh.clinic.catalog.application.usecase;

import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogListQuery;
import com.ngockhanh.clinic.catalog.application.query.ServiceCatalogQuery;
import com.ngockhanh.clinic.catalog.application.response.ServiceCatalogItemResponse;
import com.ngockhanh.clinic.shared.constants.PaginationConstants;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists the active services of the clinic catalog with optional search, allowlisted sorting and
 * one-based pagination.
 *
 * <p>Only active services are listed: they are the only ones a batch can newly select. Input is
 * validated here so every supported caller gets the same bounds. The count and the page are read
 * in one repeatable-read transaction so they agree with each other. The read changes nothing and
 * records no audit event.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListServiceCatalogUseCase {
  static final int MAX_SEARCH_KEY_LENGTH = 100;
  private static final Set<String> SORT_KEYS = Set.of("id", "code", "name", "unitPrice");

  private final ServiceCatalogQuery catalog;

  /**
   * Returns one page of active services.
   *
   * @param query raw list input; {@code null} fields fall back to defaults
   * @return the requested page; items are empty when the page is past the last page
   * @throws IllegalArgumentException when the query is null or a value is out of bounds
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResponse<ServiceCatalogItemResponse> execute(ServiceCatalogListQuery query) {
    if (query == null) throw new IllegalArgumentException("List query is required");
    int page = query.page() == null ? PaginationConstants.DEFAULT_PAGE_NUMBER : query.page();
    int size = query.size() == null ? PaginationConstants.DEFAULT_PAGE_SIZE : query.size();
    if (page < PaginationConstants.DEFAULT_PAGE_NUMBER)
      throw new IllegalArgumentException("Page must be greater than or equal to 1");
    if (size < 1 || size > PaginationConstants.MAX_PAGE_SIZE)
      throw new IllegalArgumentException("Size is out of range");

    String searchKey = query.searchKey() == null ? "" : query.searchKey().trim();
    if (searchKey.length() > MAX_SEARCH_KEY_LENGTH)
      throw new IllegalArgumentException("Search key is too long");

    String sortKey =
        query.sortKey() == null || query.sortKey().isBlank()
            ? PaginationConstants.DEFAULT_SORTED_KEY
            : query.sortKey();
    if (!SORT_KEYS.contains(sortKey)) throw new IllegalArgumentException("Sort key is not allowed");

    String sortBy =
        query.sortBy() == null || query.sortBy().isBlank()
            ? PaginationConstants.DEFAULT_SORTED_BY
            : query.sortBy().trim().toUpperCase(Locale.ROOT);
    if (!sortBy.equals("ASC") && !sortBy.equals("DESC"))
      throw new IllegalArgumentException("Sort direction must be ASC or DESC");

    String pattern = likePattern(searchKey);
    long offset = (long) (page - 1) * size;
    long total = catalog.countActive(pattern);
    List<ServiceCatalogItemResponse> items =
        total == 0 || offset >= total
            ? List.of()
            : catalog.findActivePage(pattern, offset, size, sortKey, sortBy).stream()
                .map(ServiceCatalogItemResponse::from)
                .toList();
    log.debug(
        "Catalog services listed: page={}, size={}, returned={}, total={}",
        page,
        size,
        items.size(),
        total);
    return PageResponse.<ServiceCatalogItemResponse>builder()
        .items(items)
        .page(page)
        .size(size)
        .totalElements(total)
        .totalPages(Math.toIntExact((total + size - 1) / size))
        .build();
  }

  /** Builds a lower-case contains pattern in which user-typed LIKE wildcards match literally. */
  private static String likePattern(String searchKey) {
    if (searchKey.isEmpty()) return null;
    String escaped =
        searchKey
            .toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    return "%" + escaped + "%";
  }
}
