package com.ngockhanh.clinic.catalog.api.request;

import com.ngockhanh.clinic.catalog.application.query.DepartmentListQuery;

public record DepartmentListRequest(
    Integer page,
    Integer size,
    String searchKey,
    Boolean active,
    String departmentType,
    String sortKey,
    String sortBy) {

  public DepartmentListQuery toQuery() {
    return new DepartmentListQuery(page, size, searchKey, active, departmentType, sortKey, sortBy);
  }
}
