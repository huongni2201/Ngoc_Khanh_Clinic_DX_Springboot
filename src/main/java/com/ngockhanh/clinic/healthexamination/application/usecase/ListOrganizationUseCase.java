package com.ngockhanh.clinic.healthexamination.application.usecase;

import com.ngockhanh.clinic.healthexamination.application.command.ListOrganizationCommand;
import com.ngockhanh.clinic.healthexamination.application.response.OrganizationResponse;
import com.ngockhanh.clinic.healthexamination.domain.enums.OrganizationStatus;
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

/**
 * Lists active organizations using optional search, allowlisted sorting and one-based pagination.
 *
 * <p>Only {@code ACTIVE} organizations are returned. Input is validated here so every supported
 * caller gets the same bounds, not only HTTP requests.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListOrganizationUseCase {
  static final int MAX_SEARCH_KEY_LENGTH = 100;
  private static final Set<String> SORT_KEYS = Set.of("id", "taxCode", "name");
  private static final OrganizationStatus ACTIVE = OrganizationStatus.ACTIVE;

  private final OrganizationRepository organizations;

  /**
   * Returns one page of active organizations.
   *
   * @param command raw list input; {@code null} fields fall back to defaults
   * @return the requested page; items are empty when the page is past the last page
   * @throws IllegalArgumentException when the command is null or a value is out of bounds
   */
  @Transactional(readOnly = true)
  public PageResponse<OrganizationResponse> execute(ListOrganizationCommand command) {
    if (command == null) throw new IllegalArgumentException("List command is required");
    int page = command.page() == null ? PaginationConstants.DEFAULT_PAGE_NUMBER : command.page();
    int size = command.size() == null ? PaginationConstants.DEFAULT_PAGE_SIZE : command.size();
    if (page < PaginationConstants.DEFAULT_PAGE_NUMBER)
      throw new IllegalArgumentException("Page must be greater than or equal to 1");
    if (size < 1 || size > PaginationConstants.MAX_PAGE_SIZE)
      throw new IllegalArgumentException("Size is out of range");

    String searchKey = command.searchKey() == null ? "" : command.searchKey().trim();
    if (searchKey.length() > MAX_SEARCH_KEY_LENGTH)
      throw new IllegalArgumentException("Search key is too long");

    String sortKey =
        command.sortKey() == null || command.sortKey().isBlank()
            ? PaginationConstants.DEFAULT_SORTED_KEY
            : command.sortKey();
    if (!SORT_KEYS.contains(sortKey)) throw new IllegalArgumentException("Sort key is not allowed");

    String sortBy =
        command.sortBy() == null || command.sortBy().isBlank()
            ? PaginationConstants.DEFAULT_SORTED_BY
            : command.sortBy().trim().toUpperCase(Locale.ROOT);
    if (!sortBy.equals("ASC") && !sortBy.equals("DESC"))
      throw new IllegalArgumentException("Sort direction must be ASC or DESC");

    var result =
        organizations.search(
            page, size, searchKey.isEmpty() ? null : searchKey, sortKey, sortBy, ACTIVE);
    log.debug(
        "Organizations listed: page={}, size={}, returned={}, total={}",
        page,
        size,
        result.items().size(),
        result.totalElements());
    return PageResponse.<OrganizationResponse>builder()
        .items(List.copyOf(result.items().stream().map(OrganizationResponse::from).toList()))
        .page(result.page())
        .size(result.size())
        .totalElements(result.totalElements())
        .totalPages(result.totalPages())
        .build();
  }
}
