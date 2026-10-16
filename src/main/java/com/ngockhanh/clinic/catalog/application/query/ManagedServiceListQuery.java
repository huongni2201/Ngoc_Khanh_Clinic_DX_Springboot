package com.ngockhanh.clinic.catalog.application.query;

import java.util.UUID;

public record ManagedServiceListQuery(
    Integer page,
    Integer size,
    String searchKey,
    Boolean active,
    String serviceType,
    UUID performingDepartmentId,
    UUID specialtyId,
    String sortKey,
    String sortBy) {}
