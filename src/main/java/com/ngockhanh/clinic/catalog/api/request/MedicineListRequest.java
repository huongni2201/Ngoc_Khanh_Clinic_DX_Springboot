package com.ngockhanh.clinic.catalog.api.request;

import com.ngockhanh.clinic.catalog.application.query.MedicineListQuery;

public record MedicineListRequest(
    Integer page,
    Integer size,
    String searchKey,
    Boolean active,
    String dosageForm,
    String sortKey,
    String sortBy) {

  public MedicineListQuery toQuery() {
    return new MedicineListQuery(page, size, searchKey, active, dosageForm, sortKey, sortBy);
  }
}
