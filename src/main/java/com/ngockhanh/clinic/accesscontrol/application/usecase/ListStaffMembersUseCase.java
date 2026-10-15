package com.ngockhanh.clinic.accesscontrol.application.usecase;

import com.ngockhanh.clinic.accesscontrol.application.port.out.StaffMemberDirectory;
import com.ngockhanh.clinic.accesscontrol.application.query.ListStaffMembersQuery;
import com.ngockhanh.clinic.accesscontrol.application.query.StaffMemberSummary;
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
 * Lists staff members of every status with their accounts, using optional search, allowlisted
 * sorting and one-based pagination. Bounds are validated here for every supported caller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListStaffMembersUseCase {
  public static final String VIEW_PERMISSION = "STAFF_MEMBER_VIEW";
  static final Integer MAX_SEARCH_KEY_LENGTH = 100;
  private static final Set<String> SORT_KEYS = Set.of("id", "staffCode", "fullName");

  private final StaffMemberDirectory directory;

  /**
   * Returns one page of staff members.
   *
   * @param query raw list input; {@code null} fields fall back to defaults
   * @param principal authenticated caller
   * @return the requested page; items are empty when the page is past the last page
   * @throws ApplicationException of type {@code ACCESS_DENIED} without {@code STAFF_MEMBER_VIEW}
   * @throws IllegalArgumentException when the query is null or a value is out of bounds
   */
  @Transactional(readOnly = true)
  public PageResponse<StaffMemberSummary> execute(
      ListStaffMembersQuery query, UserPrincipal principal) {
    requireView(principal);
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

    PageResponse<StaffMemberSummary> result =
        directory.search(page, size, searchKey.isEmpty() ? null : searchKey, sortKey, sortBy);
    log.debug(
        "Staff members listed: page={}, size={}, returned={}, total={}",
        page,
        size,
        result.items().size(),
        result.totalElements());
    return result;
  }

  /**
   * Requires permission to list staff members. An actor ID alone proves nothing: the principal must
   * be a staff account whose roles carry the permission, whatever the caller
   * (docs/api/staff-members.md).
   */
  private static void requireView(UserPrincipal principal) {
    Boolean allowed =
        principal != null
            && principal.userId() != null
            && "STAFF".equals(principal.principalType())
            && principal.roleAssignments().stream()
                .anyMatch(role -> role.permissions().contains(VIEW_PERMISSION));
    if (!allowed)
      throw new ApplicationException(
          ApplicationException.Type.ACCESS_DENIED, "You are not authorized to perform this action");
  }
}
