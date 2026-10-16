package com.ngockhanh.clinic.accesscontrol.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.port.out.RoleDirectory;
import com.ngockhanh.clinic.accesscontrol.application.query.ListRolesQuery;
import com.ngockhanh.clinic.accesscontrol.application.query.RoleSummary;
import com.ngockhanh.clinic.accesscontrol.application.query.UserPrincipal;
import com.ngockhanh.clinic.shared.constants.PaginationConstants;
import com.ngockhanh.clinic.shared.exception.ApplicationException;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists roles of every status with their usage counts for the Role List screen (SRS UC-008, 1.3.1),
 * using optional search, status filter, allowlisted sorting and one-based pagination.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListRolesUseCase {
  public static final String MANAGE_PERMISSION = "ROLE_MANAGE";
  static final Integer MAX_SEARCH_KEY_LENGTH = 100;
  private static final Set<String> SORT_KEYS = Set.of("id", "code", "name");

  private final RoleDirectory directory;

  /**
   * Returns one page of roles.
   *
   * @param query raw list input; {@code null} fields fall back to defaults
   * @param principal authenticated caller
   * @return the requested page; items are empty when the page is past the last page
   * @throws ApplicationException of type {@code ACCESS_DENIED} without {@code ROLE_MANAGE}
   * @throws IllegalArgumentException when the query is null or a value is out of bounds
   */
  @Transactional(readOnly = true)
  public PageResponse<RoleSummary> execute(ListRolesQuery query, UserPrincipal principal) {
    requirePermission(principal);
    if (query == null) throw new IllegalArgumentException("List query is required");
    Integer page = query.page() == null ? PaginationConstants.DEFAULT_PAGE_NUMBER : query.page();
    Integer size = query.size() == null ? PaginationConstants.DEFAULT_PAGE_SIZE : query.size();
    if (page < PaginationConstants.DEFAULT_PAGE_NUMBER)
      throw new IllegalArgumentException("Page must be greater than or equal to 1");
    if (size < 1 || size > PaginationConstants.MAX_PAGE_SIZE)
      throw new IllegalArgumentException("Size is out of range");

    String searchKey = query.searchKey() == null ? "" : query.searchKey().trim();
    if (searchKey.length() > MAX_SEARCH_KEY_LENGTH)
      throw new IllegalArgumentException("Search key is too long");

    Boolean active = activeFilter(query.status());

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

    PageResponse<RoleSummary> result =
        directory.search(
            page, size, searchKey.isEmpty() ? null : searchKey, active, sortKey, sortBy);
    log.debug(
        "Roles listed: page={}, size={}, returned={}, total={}",
        page,
        size,
        result.items().size(),
        result.totalElements());
    return result;
  }

  /** {@code ACTIVE} or {@code INACTIVE}; blank means every status. */
  private static Boolean activeFilter(String status) {
    if (status == null || status.isBlank()) return null;
    return switch (status.trim()) {
      case "ACTIVE" -> Boolean.TRUE;
      case "INACTIVE" -> Boolean.FALSE;
      default -> throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE");
    };
  }

  /**
   * Requires permission to view roles. An actor ID alone proves nothing: the principal must be a
   * staff account whose roles carry the permission (docs/api/roles-and-permissions.md).
   */
  private static void requirePermission(UserPrincipal principal) {
    Boolean allowed =
        principal != null
            && principal.userId() != null
            && "STAFF".equals(principal.principalType())
            && principal.roleAssignments().stream()
                .anyMatch(role -> role.permissions().contains(MANAGE_PERMISSION));
    if (!allowed)
      throw new ApplicationException(
          ApplicationException.Type.ACCESS_DENIED, "You are not authorized to perform this action");
  }
}
