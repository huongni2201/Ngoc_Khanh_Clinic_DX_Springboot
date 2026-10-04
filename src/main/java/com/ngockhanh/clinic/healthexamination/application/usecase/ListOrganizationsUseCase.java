package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.healthexamination.application.query.OrganizationListQuery;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationResponse;
import com.ngockhanh.clinic.healthexamination.domain.repository.OrganizationRepository;
import com.ngockhanh.clinic.shared.constants.PaginationConstants;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListOrganizationsUseCase {
  private final OrganizationRepository organizations;

  @Transactional(readOnly = true)
  public PageResponse<OrganizationResponse> execute(OrganizationListQuery query) {
    if (query == null) throw new IllegalArgumentException("Organization list query is required");

    int page = query.page() == null ? PaginationConstants.DEFAULT_PAGE_NUMBER : query.page();
    int size = query.size() == null ? PaginationConstants.DEFAULT_PAGE_SIZE : query.size();
    if (page < 1 || size < 1 || size > PaginationConstants.MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("Invalid organization pagination");
    }

    String searchKey = normalize(query.searchKey());
    String searchPattern = searchKey == null ? null : toContainsPattern(searchKey);
    String status = normalize(query.status());
    if (status != null) {
      status = status.toUpperCase(Locale.ROOT);
      if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
        throw new IllegalArgumentException("Invalid organization status");
      }
    }

    String sortKey = normalizeOrDefault(query.sortKey(), PaginationConstants.DEFAULT_SORTED_KEY);
    if (!Set.of(
            "id",
            "code",
            "name",
            "taxCode",
            "contactFullName",
            "contactPhone",
            "status",
            "createdAt")
        .contains(sortKey)) {
      throw new IllegalArgumentException("Invalid sort key");
    }
    String sortBy =
        normalizeOrDefault(query.sortBy(), PaginationConstants.DEFAULT_SORTED_BY)
            .toUpperCase(Locale.ROOT);
    if (!sortBy.equals("ASC") && !sortBy.equals("DESC")) {
      throw new IllegalArgumentException("Invalid sort direction");
    }

    long offset = (page - 1L) * size;
    if (offset > Integer.MAX_VALUE)
      throw new IllegalArgumentException("Invalid organization pagination");

    long total = organizations.countAll(searchPattern, status);
    int totalPages = total == 0 ? 0 : (int) ((total - 1) / size + 1);
    List<OrganizationResponse> items =
        organizations.findPage(offset, size, searchPattern, status, sortKey, sortBy).stream()
            .map(OrganizationResponse::from)
            .toList();

    log.info("Organizations retrieved: page={}, size={}, total={}", page, size, total);
    return new PageResponse<>(items, page, size, total, totalPages);
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String normalizeOrDefault(String value, String defaultValue) {
    String normalized = normalize(value);
    return normalized == null ? defaultValue : normalized;
  }

  private static String toContainsPattern(String value) {
    String escapedValue =
        value
            .toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    return "%" + escapedValue + "%";
  }
}
