package com.ngockhanh.clinic.catalog.application.query;

public record DepartmentListQuery(
    Integer page,
    Integer size,
    String searchKey,
    Boolean active,
    String departmentType,
    String sortKey,
    String sortBy) {}
