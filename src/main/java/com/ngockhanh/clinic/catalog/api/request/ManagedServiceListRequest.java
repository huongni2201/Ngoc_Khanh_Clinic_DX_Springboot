package com.ngockhanh.clinic.catalog.api.request;

import com.ngockhanh.clinic.catalog.application.query.ManagedServiceListQuery;
import java.util.UUID;

public record ManagedServiceListRequest(
    Integer page,
    Integer size,
    String searchKey,
    Boolean active,
    String serviceType,
    UUID performingDepartmentId,
    UUID specialtyId,
    String sortKey,
    String sortBy) {

  public ManagedServiceListQuery toQuery() {
    return new ManagedServiceListQuery(
        page,
        size,
        searchKey,
        active,
        serviceType,
        performingDepartmentId,
        specialtyId,
        sortKey,
        sortBy);
  }
}
