package com.ngockhanh.clinic.accesscontrol.api.request;

import com.ngockhanh.clinic.accesscontrol.application.query.ListRolesQuery;
import com.ngockhanh.clinic.shared.validation.AllowedSortKeys;
import com.ngockhanh.clinic.shared.web.BasePagination;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Query parameters for listing roles (docs/api/roles-and-permissions.md). {@code status} is {@code
 * ACTIVE} or {@code INACTIVE}; omitted lists every status.
 */
@NoArgsConstructor
@SuperBuilder
@Getter
@Setter
@AllowedSortKeys({"id", "code", "name"})
public class ListRoleRequest extends BasePagination {
  @Pattern(regexp = "ACTIVE|INACTIVE", message = "Status must be ACTIVE or INACTIVE")
  private String status;

  public ListRolesQuery toQuery() {
    return ListRolesQuery.builder()
        .page(getPage())
        .size(getSize())
        .searchKey(getSearchKey())
        .status(status)
        .sortKey(getSortKey())
        .sortBy(getSortBy())
        .build();
  }
}
