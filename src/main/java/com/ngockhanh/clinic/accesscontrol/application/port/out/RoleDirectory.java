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
   * Returns one page of roles of every status, ordered by code.
   *
   * @param page one-based page
   * @param size page size
   * @return the page; items are empty past the last page
   */
  PageResponse<RoleSummary> search(Integer page, Integer size);

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
