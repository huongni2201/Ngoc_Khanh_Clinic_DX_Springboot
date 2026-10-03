package com.ngockhanh.clinic.healthexamination.application.query;

import lombok.Builder;

@Builder
public record OrganizationListQuery(
    Integer page, Integer size, String searchKey, String status, String sortBy, String sortKey) {}
