package com.ngockhanh.clinic.catalog.api.request;

import com.ngockhanh.clinic.catalog.application.query.SpecialtyListQuery;

public record SpecialtyListRequest(
    Integer page, Integer size, String searchKey, Boolean active, String sortKey, String sortBy) {

  public SpecialtyListQuery toQuery() {
    return new SpecialtyListQuery(page, size, searchKey, active, sortKey, sortBy);
  }
}
