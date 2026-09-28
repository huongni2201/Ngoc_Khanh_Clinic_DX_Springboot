package com.ngockhanh.clinic.healthcheck.application.query;

import lombok.Builder;

@Builder
public record EmployeeListQuery(
    Integer page,
    Integer size,
    String searchKey,
    String sortBy,
    String sortType
) {}
