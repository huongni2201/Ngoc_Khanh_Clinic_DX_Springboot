package com.ngockhanh.clinic.catalog.application.query;

public record SpecialtyListQuery(
    Integer page, Integer size, String searchKey, Boolean active, String sortKey, String sortBy) {}
