package com.ngockhanh.clinic.catalog.application.query;

public record MedicineListQuery(
    Integer page,
    Integer size,
    String searchKey,
    Boolean active,
    String dosageForm,
    String sortKey,
    String sortBy) {}
