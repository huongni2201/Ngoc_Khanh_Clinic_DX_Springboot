package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.healthexamination.application.query.HealthExaminationBatchListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.BatchSummaryResponse;
import com.ngockhanh.clinic.healthexamination.domain.repository.HealthExaminationBatchRepository;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.healthexamination.domain.valueobject.AggregateId;
import com.ngockhanh.clinic.shared.constants.PaginationConstants;
import com.ngockhanh.clinic.shared.exception.ResourceNotFoundException;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists the health examination batches of an organization with optional search, allowlisted
 * sorting and one-based pagination.
 *
 * <p>Deleted batches are never listed. The organization must exist but may be inactive. Input is
 * validated here so every supported caller gets the same bounds. The count and the page are read in
 * one repeatable-read transaction so they agree with each other.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListHealthExaminationBatchUseCase {
  static final int MAX_SEARCH_KEY_LENGTH = 100;
  private static final Set<String> SORT_KEYS =
      Set.of("id", "batchCode", "batchName", "startDate", "status", "createdAt");

  private final OrganizationRepository organizations;
  private final HealthExaminationBatchRepository batches;

  /**
   * Returns one page of batches.
   *
   * @param organizationId owning organization
   * @param query raw list input; {@code null} fields fall back to defaults
   * @return the requested page; items are empty when the page is past the last page
   * @throws IllegalArgumentException when an argument is null or a value is out of bounds
   * @throws ResourceNotFoundException when the organization does not exist
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResponse<BatchSummaryResponse> execute(
      UUID organizationId, HealthExaminationBatchListQuery query) {
    if (organizationId == null || query == null)
      throw new IllegalArgumentException("Organization ID and list query are required");
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

    organizations
        .findById(AggregateId.of(organizationId))
        .orElseThrow(() -> new ResourceNotFoundException("Organization"));

    String pattern = likePattern(searchKey);
    long offset = (long) (page - 1) * size;
    long total = batches.count(organizationId, pattern);
    List<BatchSummaryResponse> items =
        total == 0 || offset >= total
            ? List.of()
            : batches.findPage(organizationId, offset, size, pattern, sortKey, sortBy).stream()
                .map(BatchSummaryResponse::from)
                .toList();
    log.debug(
        "Health examination batches listed: organizationId={}, page={}, size={}, returned={},"
            + " total={}",
        organizationId,
        page,
        size,
        items.size(),
        total);
    return PageResponse.<BatchSummaryResponse>builder()
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
