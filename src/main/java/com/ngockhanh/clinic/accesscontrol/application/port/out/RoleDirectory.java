package com.ngockhanh.clinic.accesscontrol.application.port.out;

import com.ngockhanh.clinic.accesscontrol.application.query.PermissionCatalogItem;
import com.ngockhanh.clinic.accesscontrol.application.query.RoleSummary;
import com.ngockhanh.clinic.shared.web.PageResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read model of role administration: roles with their usage counts and the permission catalogue.
 */
public interface RoleDirectory {
  /**
   * Returns one page of roles of every status, or only active or inactive ones.
   *
   * @param page one-based page
   * @param size page size
   * @param searchKey contains match on the role name, or null for all
   * @param active true for active roles, false for inactive ones, null for both
   * @param sortKey already allowlisted: {@code id}, {@code code} or {@code name}
   * @param sortBy {@code ASC} or {@code DESC}
   * @return the page; items are empty past the last page
   */
  PageResponse<RoleSummary> search(
      Integer page, Integer size, String searchKey, Boolean active, String sortKey, String sortBy);

  /**
   * Finds one role with its usage counts.
   *
   * @param roleId role identifier
   * @return the role, or empty when it does not exist
   */
  Optional<RoleSummary> findSummary(UUID roleId);

  /** Every permission of the catalogue, ordered by module and code. */
  List<PermissionCatalogItem> findPermissionCatalog();
}
