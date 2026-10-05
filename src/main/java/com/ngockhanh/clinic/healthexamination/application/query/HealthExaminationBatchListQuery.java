package com.ngockhanh.clinic.healthexamination.application.query;

import lombok.Builder;

@Builder
public record HealthExaminationBatchListQuery(
    Integer page, Integer size, String searchKey, String sortKey, String sortBy) {}
