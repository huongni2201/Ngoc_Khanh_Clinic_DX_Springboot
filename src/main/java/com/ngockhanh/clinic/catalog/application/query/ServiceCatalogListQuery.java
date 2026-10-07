package com.ngockhanh.clinic.catalog.application.query;

import lombok.Builder;

@Builder
public record ServiceCatalogListQuery(
    Integer page, Integer size, String searchKey, String sortKey, String sortBy) {}
