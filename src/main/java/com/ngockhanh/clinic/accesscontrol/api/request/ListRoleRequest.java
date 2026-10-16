package com.ngockhanh.clinic.accesscontrol.api.request;

import com.ngockhanh.clinic.accesscontrol.application.query.ListRolesQuery;
import com.ngockhanh.clinic.shared.constants.PaginationConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Query parameters for listing roles (docs/api/roles-and-permissions.md). Roles come in code order;
 * screens search, filter and sort them on the names they display.
 */
@NoArgsConstructor
@Getter
@Setter
public class ListRoleRequest {
  @Min(
      value = PaginationConstants.DEFAULT_PAGE_NUMBER,
      message = "Page must be greater than or equal to 1")
  private Integer page = PaginationConstants.DEFAULT_PAGE_NUMBER;

  @Min(value = 1, message = "Size must be greater than or equal to 1")
  @Max(value = PaginationConstants.MAX_PAGE_SIZE, message = "Size must not exceed 100")
  private Integer size = PaginationConstants.DEFAULT_PAGE_SIZE;

  public ListRolesQuery toQuery() {
    return ListRolesQuery.builder().page(page).size(size).build();
  }
}
